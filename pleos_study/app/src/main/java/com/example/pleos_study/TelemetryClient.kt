package com.example.pleos_study

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads newline-delimited JSON telemetry from a TCP server and reconnects forever.
 * Only the newest frame is delivered to the main thread, so a slow UI never builds a backlog.
 */
class TelemetryClient(
    private val host: String,
    private val port: Int,
    private val onTelemetry: (Telemetry) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val latest = AtomicReference<Telemetry?>(null)
    private val deliverPending = AtomicBoolean(false)
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "telemetry-client").apply { start() }
    }

    fun stop() {
        running = false
        socket?.close()
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            try {
                Socket().use { s ->
                    socket = s
                    s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    s.soTimeout = READ_TIMEOUT_MS
                    postConnection(true)
                    val reader = s.getInputStream().bufferedReader()
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        try {
                            deliver(Telemetry.fromJson(line))
                        } catch (e: org.json.JSONException) {
                            Log.w(TAG, "Bad telemetry line: $line", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Telemetry connection to $host:$port failed: ${e.message}")
            }
            socket = null
            postConnection(false)
            try {
                Thread.sleep(RETRY_DELAY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun deliver(t: Telemetry) {
        latest.set(t)
        if (deliverPending.compareAndSet(false, true)) {
            main.post {
                deliverPending.set(false)
                latest.getAndSet(null)?.let { if (running) onTelemetry(it) }
            }
        }
    }

    private fun postConnection(connected: Boolean) {
        main.post { if (running) onConnectionChanged(connected) }
    }

    companion object {
        private const val TAG = "TelemetryClient"
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val READ_TIMEOUT_MS = 3000
        private const val RETRY_DELAY_MS = 1000L
    }
}
