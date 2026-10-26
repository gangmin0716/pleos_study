package com.example.pleos_study

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

/**
 * Now playing from whichever media app is active, laid out like a full-screen music player:
 * the album art, blurred, fills the card behind large art, title, progress and transport.
 *
 * Two sources: media sessions on the head unit (apps, a phone over Bluetooth) and, in debug
 * builds, the development Mac through tools/mac_media_bridge.py. Whichever is playing is shown,
 * the head unit first; controls go to the source on screen.
 */
class MusicWidget(
    private val activity: Activity,
    parent: ViewGroup,
) : DashboardWidget(parent, R.layout.widget_music) {

    private val backdrop: ImageView = find(R.id.musicBackdrop)
    private val source: TextView = find(R.id.musicSource)
    private val title: TextView = find(R.id.musicTitle)
    private val artist: TextView = find(R.id.musicArtist)
    private val art: ImageView = find(R.id.musicArt)
    private val progress: BarView = find(R.id.musicProgress)
    private val elapsed: TextView = find(R.id.musicElapsed)
    private val remaining: TextView = find(R.id.musicRemaining)
    private val playPause: ImageButton = find(R.id.musicPlayPause)
    private val shuffle: ImageButton = find(R.id.musicShuffle)
    private val repeat: ImageButton = find(R.id.musicRepeat)
    private val info: View = find(R.id.musicInfo)
    private val watcher = NowPlayingWatcher(context) { sessionTrack = it; show() }
    private val mac: MacMediaClient? =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            MacMediaClient(context, MAC_BRIDGE_HOST, MAC_BRIDGE_PORT) { macTrack = it; show() }
        } else {
            null
        }
    private var sessionTrack: NowPlayingWatcher.NowPlaying? = null
    private var macTrack: NowPlayingWatcher.NowPlaying? = null
    private var showingMac = false
    private var nowPlaying: NowPlayingWatcher.NowPlaying? = null
    private var backdropArt: Bitmap? = null

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            renderPosition()
            if (nowPlaying?.playing == true) progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    init {
        // Keep the oversized, blurred backdrop inside the card's rounded corners
        view.clipToOutline = true
        progress.trackColor = color(R.color.alpha_light_200)
        progress.fillColor = color(R.color.static_light_200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Blow the art up past the card edges so the blur has no hard border
            backdrop.scaleX = BACKDROP_SCALE
            backdrop.scaleY = BACKDROP_SCALE
            val blur = context.resources.displayMetrics.density * BLUR_RADIUS_DP
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
        } else {
            // No blur before Android 12: sharp art behind the text is unreadable, so keep the plain ground
            backdrop.visibility = View.GONE
        }

        // Prefer the watched session's controls; without access, fall back to media keys,
        // which go to whichever app holds the active media session
        val audio = context.getSystemService(AudioManager::class.java)
        fun mediaButton(id: Int, keyCode: Int, macCmd: String, action: (MediaControllerCompat.TransportControls) -> Unit) =
            find<View>(id).setOnClickListener {
                val controls = watcher.transportControls
                if (showingMac) {
                    mac?.send(macCmd)
                } else if (controls != null) {
                    action(controls)
                } else {
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
        mediaButton(R.id.musicPrev, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous") { it.skipToPrevious() }
        mediaButton(R.id.musicPlayPause, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "playpause") {
            if (nowPlaying?.playing == true) it.pause() else it.play()
        }
        mediaButton(R.id.musicNext, KeyEvent.KEYCODE_MEDIA_NEXT, "next") { it.skipToNext() }

        shuffle.setOnClickListener {
            if (showingMac) {
                mac?.send("shuffle")
                return@setOnClickListener
            }
            val on = nowPlaying?.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
            watcher.transportControls?.setShuffleMode(
                if (on) PlaybackStateCompat.SHUFFLE_MODE_NONE else PlaybackStateCompat.SHUFFLE_MODE_ALL
            )
        }
        repeat.setOnClickListener {
            if (showingMac) {
                mac?.send("repeat")
                return@setOnClickListener
            }
            // Off → all → one → off, like most players
            val next = when (nowPlaying?.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
            watcher.transportControls?.setRepeatMode(next)
        }
        renderModes(null)
    }

    override fun onResume() {
        mac?.start()
        // Also runs after coming back from the notification access screen
        val hasAccess = watcher.start()
        // Only tappable while asking for access: a clickable view takes rotary focus,
        // and AAOS paints a grey highlight over it
        if (hasAccess) {
            info.setOnClickListener(null)
            info.isClickable = false
            info.isFocusable = false
        } else {
            info.setOnClickListener { openNotificationAccessSettings() }
            show()
        }
    }

    override fun onStop() {
        watcher.stop()
        mac?.stop()
        progressHandler.removeCallbacks(progressTick)
    }

    /** The head unit's track if it's playing, else the Mac's if that is, else whatever is paused. */
    private fun show() {
        val session = sessionTrack
        val fromMac = macTrack
        val track = when {
            session?.playing == true -> session
            fromMac?.playing == true -> fromMac
            else -> session ?: fromMac
        }
        showingMac = track != null && track === fromMac
        renderNowPlaying(track)
        if (track == null && !watcher.hasAccess) {
            title.setText(R.string.music_no_access_title)
            artist.setText(R.string.music_no_access_subtitle)
        }
    }

    private fun renderNowPlaying(track: NowPlayingWatcher.NowPlaying?) {
        nowPlaying = track
        if (track == null) {
            title.setText(R.string.music_idle_title)
            artist.setText(R.string.music_idle_subtitle)
        } else {
            title.text = track.title ?: context.getString(R.string.music_unknown_title)
            artist.text = track.artist.orEmpty()
        }
        source.text = track?.source
        source.visibility = if (track?.source != null) View.VISIBLE else View.GONE
        val cover = track?.art
        art.setImageBitmap(cover)
        art.visibility = if (cover != null) View.VISIBLE else View.GONE
        // Re-blurring is the expensive part: only when the art actually changes
        if (cover !== backdropArt) {
            backdropArt = cover
            backdrop.setImageBitmap(cover)
        }
        playPause.setImageResource(if (track?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
        renderModes(track)
        progressHandler.removeCallbacks(progressTick)
        progressTick.run()
    }

    private fun renderPosition() {
        val track = nowPlaying
        val duration = track?.durationMs ?: 0L
        if (track == null || duration <= 0) {
            progress.update(0f)
            elapsed.text = ""
            remaining.text = ""
            return
        }
        val position = track.positionMs().coerceIn(0L, duration)
        progress.update(position.toFloat() / duration)
        elapsed.text = formatTime(position)
        remaining.text = "-" + formatTime(duration - position)
    }

    /** Shuffle and repeat light up when on, and fade out when the app doesn't support them. */
    private fun renderModes(track: NowPlayingWatcher.NowPlaying?) {
        val shuffleMode = track?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_INVALID
        val repeatMode = track?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_INVALID
        setMode(shuffle, shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        setMode(repeat, repeatMode != PlaybackStateCompat.REPEAT_MODE_INVALID, repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL || repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE)
        repeat.setImageResource(if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) R.drawable.ic_media_repeat_one else R.drawable.ic_media_repeat)
    }

    private fun setMode(button: ImageButton, supported: Boolean, on: Boolean) {
        button.isEnabled = supported
        button.alpha = if (supported) 1f else DISABLED_ALPHA
        button.setBackgroundResource(if (on) R.drawable.bg_glyph_active else R.drawable.bg_glyph_button)
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun openNotificationAccessSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No notification access screen on this system", e)
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val BLUR_RADIUS_DP = 60f
        private const val BACKDROP_SCALE = 1.4f
        private const val DISABLED_ALPHA = 0.35f
        private const val TAG = "MusicWidget"
        // The emulator's address for the Mac it runs on
        private const val MAC_BRIDGE_HOST = "10.0.2.2"
        private const val MAC_BRIDGE_PORT = 9100
    }
}
