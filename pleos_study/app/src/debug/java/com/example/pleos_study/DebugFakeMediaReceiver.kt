package com.example.pleos_study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Debug builds only: publishes fake "now playing" media sessions so the music card can be
 * tested on an emulator without real music apps. Supports play/pause, shuffle and repeat.
 * Each `tag` is its own session, standing in for a different app; `artUri` hands the cover
 * over as a file URI the way Bluetooth and many streaming apps do; `playing false` starts paused.
 *
 *   adb shell "am broadcast --user 10 -n com.example.pleos_study/.DebugFakeMediaReceiver \
 *       --es tag spotify --es title 'Song' --es artist 'Artist' --ez artUri true"
 *   adb shell am broadcast --user 10 -n com.example.pleos_study/.DebugFakeMediaReceiver --ez stop true
 */
class DebugFakeMediaReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: "DebugFakeMedia"
        if (intent.getBooleanExtra("stop", false)) {
            // Without a tag, stop them all
            if (intent.hasExtra("tag")) sessions.remove(tag)?.release()
            else sessions.values.forEach { it.release() }.also { sessions.clear() }
            return
        }
        sessions.remove(tag)?.release()

        val durationMs = intent.getLongExtra("durationMs", 214_000L)
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, intent.getStringExtra("title") ?: "Test Track")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, intent.getStringExtra("artist") ?: "Test Artist")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        val hue = intent.getIntExtra("hue", 0)
        if (intent.getBooleanExtra("artUri", false)) {
            val file = File(context.cacheDir, "fake-art-$tag.png")
            FileOutputStream(file).use { fakeArt(hue).compress(Bitmap.CompressFormat.PNG, 100, it) }
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, Uri.fromFile(file).toString())
        } else {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, fakeArt(hue))
        }
        sessions[tag] = MediaSessionCompat(context.applicationContext, tag).apply {
            setMetadata(metadata.build())
            var position = intent.getLongExtra("positionMs", 0L)
            var playing = intent.getBooleanExtra("playing", true)
            var startedAt = SystemClock.elapsedRealtime()
            fun publishState() = setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    )
                    .setState(
                        if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        position, 1f, SystemClock.elapsedRealtime(),
                    )
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startedAt = SystemClock.elapsedRealtime()
                    playing = true
                    publishState()
                }

                override fun onPause() {
                    position += SystemClock.elapsedRealtime() - startedAt
                    playing = false
                    publishState()
                }

                override fun onSetShuffleMode(shuffleMode: Int) = setShuffleMode(shuffleMode)
                override fun onSetRepeatMode(repeatMode: Int) = setRepeatMode(repeatMode)
            })
            setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
            setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
            publishState()
            isActive = true
        }
    }

    /** A leafy green square (turned round the colour wheel by [hue] degrees) with a warm light spot. */
    private fun fakeArt(hue: Int): Bitmap {
        fun c(argb: Long): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(argb.toInt(), hsv)
            hsv[0] = (hsv[0] + hue) % 360f
            return Color.HSVToColor(Color.alpha(argb.toInt()), hsv)
        }
        val size = 512f
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRect(0f, 0f, size, size, Paint().apply {
            shader = LinearGradient(0f, 0f, size, size, c(0xFF2F5A1E), c(0xFF6B7A2A), Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(size * 0.7f, size * 0.3f, size * 0.45f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(size * 0.7f, size * 0.3f, size * 0.45f, c(0x99D9C27A), c(0x00D9C27A), Shader.TileMode.CLAMP)
        })
        canvas.drawRect(size * 0.42f, size * 0.2f, size * 0.58f, size, Paint().apply { color = c(0xFF8FB35A) })
        return bmp
    }

    companion object {
        private val sessions = HashMap<String, MediaSessionCompat>()
    }
}
