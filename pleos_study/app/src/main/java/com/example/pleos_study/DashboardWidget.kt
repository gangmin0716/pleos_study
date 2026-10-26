package com.example.pleos_study

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat

/** One card on the dashboard grid. Each widget owns its own views and rendering. */
abstract class DashboardWidget(parent: ViewGroup, @LayoutRes layout: Int) {

    val view: View = LayoutInflater.from(parent.context).inflate(layout, parent, false)
    protected val context get() = view.context

    open fun render(t: Telemetry) {}
    open fun onResume() {}
    open fun onStop() {}

    protected fun <T : View> find(@IdRes id: Int): T = view.findViewById(id)
    protected fun color(@ColorRes id: Int) = ContextCompat.getColor(context, id)

    companion object {
        /** Widget types usable in assets/dashboard_grid.json. */
        val TYPES: Map<String, (Activity, ViewGroup) -> DashboardWidget> = mapOf(
            "cluster" to { _, parent -> ClusterWidget(parent) },
            "music" to { activity, parent -> MusicWidget(activity, parent) },
            "map" to { _, parent -> TrackMapWidget(parent) },
            "laps" to { _, parent -> LapTimesWidget(parent) },
            "fuel" to { _, parent -> FuelWidget(parent) },
            "tyres" to { _, parent -> TyresWidget(parent) },
        )
    }
}
