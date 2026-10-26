package com.example.pleos_study

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Base64
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * What the development Mac is playing (Apple Music, Spotify), from tools/mac_media_bridge.py.
 * The emulator can't see the Mac's media or pair with it over Bluetooth, so the bridge streams
 * the player state here as JSON lines and takes transport commands back on the same socket.
 * Reconnects forever; reports null whenever the bridge is away or the Mac plays nothing.
 */
class MacMediaClient(
    private val context: Context,
    private val host: String,
    private val port: Int,
    private val onChange: (NowPlayingWatcher.NowPlaying?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    private var thread: Thread? = null
    private var artKey: String? = null
    private var art: Bitmap? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "mac-media-client").apply { start() }
    }

    fun stop() {
        running = false
        socket?.close()
        thread?.interrupt()
        thread = null
    }

    /** Sends "playpause", "next", "previous", "shuffle" or "repeat" to the Mac's player. */
    fun send(cmd: String) {
        writer.execute {
            try {
                output?.write("{\"cmd\":\"$cmd\"}\n".toByteArray())
            } catch (e: IOException) {
                Log.d(TAG, "Couldn't send $cmd: ${e.message}")
            }
        }
    }

    private fun loop() {
        while (running) {
            try {
                Socket().use { s ->
                    socket = s
                    s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    s.soTimeout = READ_TIMEOUT_MS
                    output = s.getOutputStream()
                    val reader = s.getInputStream().bufferedReader()
                    while (running) {
                        val line = reader.readLine() ?: break
                        try {
                            val track = parse(JSONObject(line))
                            main.post { if (running) onChange(track) }
                        } catch (e: JSONException) {
                            Log.w(TAG, "Bad line from the Mac bridge", e)
                        }
                    }
                }
            } catch (e: IOException) {
                // No bridge running is the normal case; stay quiet and retry
            }
            output = null
            socket = null
            main.post { if (running) onChange(null) }
            try {
                Thread.sleep(RETRY_DELAY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun parse(o: JSONObject): NowPlayingWatcher.NowPlaying? {
        val source = o.optString("source").ifEmpty { return null }
        val key = o.optString("artKey")
        if (key != artKey) {
            artKey = key
            art = null
        }
        o.optString("art").takeIf { it.isNotEmpty() }?.let {
            val bytes = Base64.decode(it, Base64.DEFAULT)
            art = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        return NowPlayingWatcher.NowPlaying(
            title = o.optString("title").ifEmpty { null },
            artist = o.optString("artist").ifEmpty { null },
            art = art,
            source = context.getString(
                if (source == "Spotify") R.string.music_source_mac_spotify else R.string.music_source_mac_music
            ),
            playing = o.optBoolean("playing"),
            durationMs = o.optLong("durationMs"),
            shuffleMode = if (o.optBoolean("shuffle")) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE,
            repeatMode = when (o.optString("repeat")) {
                "all" -> PlaybackStateCompat.REPEAT_MODE_ALL
                "one" -> PlaybackStateCompat.REPEAT_MODE_ONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            },
            positionAtUpdate = o.optLong("positionMs"),
            speed = 1f,
            updatedAt = SystemClock.elapsedRealtime(),
        )
    }

    companion object {
        private const val TAG = "MacMediaClient"
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val READ_TIMEOUT_MS = 5000
        private const val RETRY_DELAY_MS = 2000L
    }
}
