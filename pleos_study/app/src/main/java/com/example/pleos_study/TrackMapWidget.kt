package com.example.pleos_study

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Track map learned from the car's own lap, with the car position and lap progress. */
class TrackMapWidget(parent: ViewGroup) : DashboardWidget(parent, R.layout.widget_map) {

    private val trackMap: TrackMapView = find(R.id.trackMap)
    private val trackHint: TextView = find(R.id.trackHint)
    private val lapProgressText: TextView = find(R.id.lapProgressText)
    private val recorder = TrackRecorder(context)

    init {
        trackHint.setText(R.string.track_waiting)
    }

    override fun render(t: Telemetry) {
        val position = t.lapPosition
        if (position == null || t.carX == null || t.carZ == null) return
        lapProgressText.text = "${(position * 100).toInt()}%"
        if (recorder.onSample(t.trackName ?: UNKNOWN_TRACK, position, t.carX, t.carZ)) {
            trackMap.setOutline(recorder.outline)
        }
        trackMap.update(recorder.trail, t.carX, t.carZ, position)
        if (recorder.outline != null) {
            trackHint.visibility = View.GONE
        } else {
            trackHint.visibility = View.VISIBLE
            trackHint.setText(R.string.track_learning)
        }
    }

    companion object {
        private const val UNKNOWN_TRACK = "unknown"
    }
}
