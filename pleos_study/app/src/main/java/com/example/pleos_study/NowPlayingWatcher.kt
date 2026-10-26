package com.example.pleos_study

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Follows whichever app is playing media and reports what it's playing: apps on the head unit
 * (Spotify, a radio app) and a phone's player over Bluetooth, which Android Automotive exposes
 * as a com.android.bluetooth media session (Apple Music, Spotify, anything the phone plays).
 *
 * Every active session is watched, so whichever starts playing takes over the card. The chosen
 * one is driven through MediaControllerCompat, which also exposes shuffle and repeat.
 */
class NowPlayingWatcher(
    private val context: Context,
    private val onChange: (NowPlaying?) -> Unit,
) {
    data class NowPlaying(
        val title: String?,
        val artist: String?,
        val art: Bitmap?,
        /** Where the music comes from: an app name, or Bluetooth for a phone. */
        val source: String?,
        val playing: Boolean,
        val durationMs: Long,
        /** PlaybackStateCompat.SHUFFLE_MODE_*; SHUFFLE_MODE_INVALID when the app doesn't support it. */
        val shuffleMode: Int,
        /** PlaybackStateCompat.REPEAT_MODE_*; REPEAT_MODE_INVALID when the app doesn't support it. */
        val repeatMode: Int,
        private val positionAtUpdate: Long,
        private val speed: Float,
        private val updatedAt: Long,
    ) {
        fun positionMs(now: Long = SystemClock.elapsedRealtime()): Long =
            if (playing) positionAtUpdate + ((now - updatedAt) * speed).toLong() else positionAtUpdate
    }

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaControllerCompat? = null
    private var started = false

    /** Every active session, watched only to notice when a different app starts playing. */
    private val watched = HashMap<MediaSession.Token, MediaController>()
    private val anySessionCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = repick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = repick()
        override fun onSessionDestroyed() = repick()
    }

    private val callback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = publish()
        override fun onShuffleModeChanged(shuffleMode: Int) = publish()
        override fun onRepeatModeChanged(repeatMode: Int) = publish()
        // Shuffle and repeat only read correctly once the compat link to the session is up
        override fun onSessionReady() = publish()
        override fun onSessionDestroyed() = repick()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    // Album art that arrives as a URI (Bluetooth cover art, many streaming apps) is decoded off
    // the main thread; one entry is enough since only the current track is shown
    private val artLoader = Executors.newSingleThreadExecutor()
    private var artUri: String? = null
    private var artFromUri: Bitmap? = null

    val hasAccess: Boolean
        get() = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    val transportControls: MediaControllerCompat.TransportControls? get() = controller?.transportControls

    /** Returns false when notification access hasn't been granted yet. */
    fun start(): Boolean {
        if (started) return true
        if (!hasAccess) return false
        return try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            started = true
            repick()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop() {
        if (!started) return
        sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        watched.values.forEach { it.unregisterCallback(anySessionCallback) }
        watched.clear()
        controller?.unregisterCallback(callback)
        controller = null
        started = false
    }

    private fun repick() {
        if (!started) return
        try {
            pick(sessions.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost notification access", e)
        }
    }

    private fun pick(controllers: List<MediaController>) {
        watchAll(controllers)
        val current = controllers.firstOrNull { MediaSessionCompat.Token.fromToken(it.sessionToken) == controller?.sessionToken }
        val next = choose(controllers, current)
        val nextToken = next?.sessionToken?.let { MediaSessionCompat.Token.fromToken(it) }
        if (nextToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = nextToken?.let { MediaControllerCompat(context, it) }
            controller?.registerCallback(callback, main)
            Log.d(TAG, "Following ${next?.packageName ?: "nothing"}")
        }
        publish()
    }

    /**
     * The app that is playing wins; the current one keeps the card while it plays, so two
     * players don't flip back and forth. Otherwise the most recent session that has something
     * to show. Sessions with no track (a disconnected Bluetooth player, the phone dialer) are
     * skipped.
     */
    private fun choose(controllers: List<MediaController>, current: MediaController?): MediaController? {
        fun hasTrack(c: MediaController) = c.metadata?.let {
            it.getString(MediaMetadata.METADATA_KEY_TITLE) != null ||
                it.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null
        } == true
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING && hasTrack(c)

        if (current != null && playing(current)) return current
        return controllers.firstOrNull(::playing)
            ?: current?.takeIf(::hasTrack)
            ?: controllers.firstOrNull { hasTrack(it) && it.playbackState?.state != PlaybackState.STATE_ERROR }
    }

    private fun watchAll(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach { watched.remove(it)?.unregisterCallback(anySessionCallback) }
        for (c in controllers) {
            if (c.sessionToken !in watched) {
                c.registerCallback(anySessionCallback, main)
                watched[c.sessionToken] = c
            }
        }
    }

    private fun publish() {
        val c = controller
        val m = c?.metadata
        if (c == null || m == null) {
            onChange(null)
            return
        }
        val state = c.playbackState
        onChange(
            NowPlaying(
                title = m.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                    ?: m.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
                artist = m.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                    ?: m.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
                    ?: m.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE),
                art = artOf(m),
                source = sourceLabel(c.packageName),
                playing = state?.state == PlaybackStateCompat.STATE_PLAYING,
                durationMs = m.getLong(MediaMetadataCompat.METADATA_KEY_DURATION),
                shuffleMode = c.shuffleMode,
                repeatMode = c.repeatMode,
                positionAtUpdate = state?.position ?: 0L,
                speed = state?.playbackSpeed ?: 1f,
                updatedAt = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            )
        )
    }

    /** A bitmap in the metadata, else one loaded from the art URI (published again when it arrives). */
    private fun artOf(m: MediaMetadataCompat): Bitmap? {
        val embedded = m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
        if (embedded != null) return embedded
        val uri = listOf(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadataCompat.METADATA_KEY_ART_URI,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
        ).firstNotNullOfOrNull { m.getString(it) } ?: return null
        if (uri == artUri) return artFromUri
        artUri = uri
        artFromUri = null
        artLoader.execute {
            val bitmap = decode(Uri.parse(uri))
            main.post {
                if (artUri == uri && bitmap != null) {
                    artFromUri = bitmap
                    publish()
                }
            }
        }
        return null
    }

    /** Local URIs only: the dashboard has no reason to reach the network for cover art. */
    private fun decode(uri: Uri): Bitmap? {
        if (uri.scheme !in LOCAL_SCHEMES) return null
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_SIZE_PX) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't load album art $uri", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to album art $uri", e)
            null
        }
    }

    private fun sourceLabel(packageName: String): String? {
        if (packageName == BLUETOOTH_PACKAGE) return context.getString(R.string.music_source_bluetooth)
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {
        private const val TAG = "NowPlayingWatcher"
        private const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
        private const val ART_SIZE_PX = 512
        private val LOCAL_SCHEMES = setOf(
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE,
            ContentResolver.SCHEME_ANDROID_RESOURCE,
        )
    }
}
