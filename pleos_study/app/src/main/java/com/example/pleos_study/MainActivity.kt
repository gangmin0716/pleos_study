package com.example.pleos_study

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Assetto Corsa dashboard. Connects to a telemetry server (tools/telemetry_sim.py, or a
 * real AC bridge later) that streams JSON lines. On the emulator the host Mac is 10.0.2.2.
 * The cards and their arrangement come from assets/dashboard_grid.json, and can be
 * rearranged by dragging in edit mode.
 *
 *   adb shell am start -n com.example.pleos_study/.MainActivity --es host 10.0.2.2 --ei port 9000
 */
class MainActivity : AppCompatActivity() {

    private lateinit var client: TelemetryClient
    private lateinit var widgets: List<DashboardWidget>

    private lateinit var sessionText: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var throttleBar: BarView
    private lateinit var brakeBar: BarView
    private lateinit var grid: DashboardGridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sessionText = findViewById(R.id.sessionText)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        throttleBar = findViewById<BarView>(R.id.throttleBar).apply { fillColor = getColor(R.color.pleos_blue) }
        brakeBar = findViewById<BarView>(R.id.brakeBar).apply { fillColor = getColor(R.color.pleos_red) }

        grid = findViewById(R.id.dashboardGrid)
        val spec = DashboardSpec.load(this)
        val byType = spec.widgetTypes.mapNotNull { type ->
            val create = DashboardWidget.TYPES[type]
            if (create == null) Log.w(TAG, "Unknown widget type '$type' in ${DashboardSpec.ASSET}")
            create?.let { type to it(this, grid) }
        }.toMap()
        widgets = byType.values.toList()
        grid.setSpec(spec, byType.mapValues { it.value.view })
        grid.forcedLayout = intent.getStringExtra(EXTRA_GRID_LAYOUT)
        grid.onSpecChanged = { it.save(this) }
        setUpLayoutEditing()
        PleosFonts.applyTo(findViewById(R.id.root))

        client = TelemetryClient(
            host = intent.getStringExtra(EXTRA_HOST) ?: DEFAULT_HOST,
            port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT),
            onTelemetry = ::render,
            onConnectionChanged = ::renderConnection,
        )
    }

    override fun onStart() {
        super.onStart()
        client.start()
    }

    override fun onResume() {
        super.onResume()
        widgets.forEach { it.onResume() }
    }

    override fun onStop() {
        client.stop()
        widgets.forEach { it.onStop() }
        super.onStop()
    }

    private fun setUpLayoutEditing() {
        val editButton: TextView = findViewById(R.id.editLayoutButton)
        val resetButton: View = findViewById(R.id.resetLayoutButton)
        editButton.setOnClickListener {
            grid.editMode = !grid.editMode
            val editing = grid.editMode
            editButton.setText(if (editing) R.string.layout_done else R.string.layout_edit)
            editButton.setBackgroundResource(if (editing) R.drawable.bg_button_filled else R.drawable.bg_button_basic)
            editButton.setTextColor(ContextCompat.getColor(this, if (editing) R.color.white else R.color.pleos_text))
            resetButton.visibility = if (editing) View.VISIBLE else View.GONE
            if (editing) sessionText.setText(R.string.layout_edit_hint)
        }
        resetButton.setOnClickListener {
            DashboardSpec.clearSaved(this)
            recreate()
        }
    }

    private fun render(t: Telemetry) {
        if (!grid.editMode) {
            sessionText.text = listOfNotNull(t.carName, t.trackName).joinToString("  ·  ")
                .ifEmpty { getString(R.string.session_waiting) }
        }
        throttleBar.update(t.gas)
        brakeBar.update(t.brake)
        widgets.forEach { it.render(t) }
    }

    private fun renderConnection(connected: Boolean) {
        statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (connected) R.color.pleos_green else R.color.pleos_text_disabled)
        )
        statusText.setText(if (connected) R.string.status_connected else R.string.status_waiting)
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        /** Force a layout by name from dashboard_grid.json, e.g. --es grid compact */
        const val EXTRA_GRID_LAYOUT = "grid"
        private const val DEFAULT_HOST = "10.0.2.2"
        private const val DEFAULT_PORT = 9000
        private const val TAG = "MainActivity"
    }
}
