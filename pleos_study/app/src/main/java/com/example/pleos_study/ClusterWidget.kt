package com.example.pleos_study

import android.view.ViewGroup
import android.widget.ImageView
import kotlin.math.roundToInt

/** Tachometer with speed, gear row and driving-assist telltales. */
class ClusterWidget(parent: ViewGroup) : DashboardWidget(parent, R.layout.widget_cluster) {

    private val rpmGauge: RpmGaugeView = find(R.id.rpmGauge)
    private val gearIndicator: GearIndicatorView = find(R.id.gearIndicator)
    private val tcTelltale = Telltale(find(R.id.tcTelltale))
    private val absTelltale = Telltale(find(R.id.absTelltale))

    init {
        tcTelltale.show(false)
        absTelltale.show(false)
    }

    override fun render(t: Telemetry) {
        rpmGauge.update(t.rpm, t.maxRpm, t.speedKmh.roundToInt())
        gearIndicator.update(t.gear)
        tcTelltale.show(t.tcActive)
        absTelltale.show(t.absActive)
    }

    /** Amber as drawn in Pleos while active; greyed out otherwise, like an unlit telltale. */
    private inner class Telltale(private val icon: ImageView) {
        private var shown: Boolean? = null

        fun show(active: Boolean) {
            if (active == shown) return
            shown = active
            if (active) icon.clearColorFilter() else icon.setColorFilter(color(R.color.basic_200))
        }
    }
}
