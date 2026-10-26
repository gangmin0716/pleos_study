package com.example.pleos_study

import android.content.Context
import android.graphics.PointF
import kotlin.math.hypot

/**
 * Learns a track outline from one clean lap of car positions (finish line to finish line)
 * and remembers it per track name, so the map is ready immediately on the next session.
 * This works the same for the simulator and for real Assetto Corsa telemetry.
 */
class TrackRecorder(context: Context) {

    private val prefs = context.getSharedPreferences("track_maps", Context.MODE_PRIVATE)
    private val points = ArrayList<PointF>()
    private var recordingLap = false
    private var lastPosition = -1f

    var trackName: String? = null
        private set
    var outline: List<PointF>? = null
        private set

    /** The partial lap recorded so far, while there is no outline yet. */
    val trail: List<PointF> get() = points

    /** Feeds one sample; returns true when [outline] changed. */
    fun onSample(track: String, position: Float, x: Float, z: Float): Boolean {
        var changed = false
        if (track != trackName) {
            trackName = track
            outline = load(track)
            points.clear()
            recordingLap = false
            lastPosition = -1f
            changed = true
        }

        val crossedLine = lastPosition > 0.9f && position < 0.1f
        // Jumping backwards without crossing the line means a reset or a trip to the pits
        val jumpedBack = !crossedLine && lastPosition >= 0f && position < lastPosition - 0.05f
        lastPosition = position
        if (outline != null) return changed

        if (jumpedBack) {
            recordingLap = false
            points.clear()
        }
        if (crossedLine) {
            if (recordingLap && points.size >= MIN_POINTS) {
                val learned = points.toList()
                outline = learned
                save(track, learned)
                points.clear()
                recordingLap = false
                return true
            }
            points.clear()
            recordingLap = true
        }
        if (recordingLap) {
            val last = points.lastOrNull()
            if (last == null || hypot(x - last.x, z - last.y) >= MIN_SPACING_M) points.add(PointF(x, z))
        }
        return changed
    }

    private fun load(track: String): List<PointF>? =
        prefs.getString(key(track), null)?.split(';')?.mapNotNull { pair ->
            val (x, z) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            PointF(x.toFloatOrNull() ?: return@mapNotNull null, z.toFloatOrNull() ?: return@mapNotNull null)
        }?.takeIf { it.size >= MIN_POINTS }

    private fun save(track: String, outline: List<PointF>) {
        prefs.edit()
            .putString(key(track), outline.joinToString(";") { "%.1f,%.1f".format(it.x, it.y) })
            .apply()
    }

    private fun key(track: String) = "outline:$track"

    companion object {
        private const val MIN_SPACING_M = 4f
        private const val MIN_POINTS = 50
    }
}
