package com.example.pleos_study

import android.view.ViewGroup
import android.widget.TextView

class FuelWidget(parent: ViewGroup) : DashboardWidget(parent, R.layout.widget_fuel) {

    private val fuelText: TextView = find(R.id.fuelText)
    private val fuelBar: BarView = find(R.id.fuelBar)

    override fun render(t: Telemetry) {
        val fuel = t.fuel ?: return
        fuelText.text = "%.1f L".format(fuel)
        fuelBar.update(t.maxFuel?.let { fuel / it } ?: 0f)
    }
}
