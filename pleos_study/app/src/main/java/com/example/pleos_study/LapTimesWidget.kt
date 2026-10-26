package com.example.pleos_study

import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.abs

/** Current lap time, lap number, last lap with its delta to the best, and best lap. */
class LapTimesWidget(parent: ViewGroup) : DashboardWidget(parent, R.layout.widget_laps) {

    private val lapNumberText: TextView = find(R.id.lapNumberText)
    private val currentLapText: TextView = find(R.id.currentLapText)
    private val lastLapText: TextView = find(R.id.lastLapText)
    private val lastLapDeltaText: TextView = find(R.id.lastLapDeltaText)
    private val bestLapText: TextView = find(R.id.bestLapText)

    init {
        lapNumberText.text = context.getString(R.string.lap) + " 1"
    }

    override fun render(t: Telemetry) {
        lapNumberText.text = context.getString(R.string.lap) + " ${t.lapCount + 1}"
        currentLapText.text = formatLap(t.lapTimeMs)
        lastLapText.text = formatLap(t.lastLapMs)
        bestLapText.text = formatLap(t.bestLapMs)
        if (t.lastLapMs > 0 && t.bestLapMs > 0) {
            val delta = t.lastLapMs - t.bestLapMs
            lastLapDeltaText.text = "%s%d.%03d".format(if (delta > 0) "+" else "±", abs(delta) / 1000, abs(delta) % 1000)
            lastLapDeltaText.setTextColor(color(if (delta > 0) R.color.pleos_red else R.color.pleos_blue))
        } else {
            lastLapDeltaText.text = ""
        }
    }

    private fun formatLap(ms: Int): String {
        if (ms <= 0) return context.getString(R.string.no_value)
        return "%d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)
    }
}
