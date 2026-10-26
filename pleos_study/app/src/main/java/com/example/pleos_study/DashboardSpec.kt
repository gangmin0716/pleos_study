package com.example.pleos_study

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * The dashboard grid. Defaults come from assets/dashboard_grid.json; changes made by dragging
 * cards in edit mode are saved to the app's files and take precedence until reset.
 * Several layouts can be given; the one with the largest minWidth that still fits the window
 * is used, so the grid reflows as the window gets narrower.
 */
data class DashboardSpec(val gapDp: Float, val layouts: List<GridLayoutSpec>) {

    /** Every widget type any layout uses. */
    val widgetTypes: Set<String> get() = layouts.flatMap { l -> l.cells.map { it.type } }.toSet()

    fun layoutFor(widthDp: Float): GridLayoutSpec =
        layouts.sortedByDescending { it.minWidthDp }.firstOrNull { widthDp >= it.minWidthDp }
            ?: layouts.minBy { it.minWidthDp }

    fun save(context: Context) {
        savedFile(context).writeText(toJson())
    }

    fun toJson(): String = JSONObject()
        .put("gap", gapDp.toDouble())
        .put("layouts", JSONArray(layouts.map { l ->
            JSONObject()
                .put("name", l.name)
                .put("minWidth", l.minWidthDp.toDouble())
                .put("columns", JSONArray(l.columns.map { it.toDouble() }))
                .put("rows", JSONArray(l.rows.map { it.toDouble() }))
                .put("widgets", JSONArray(l.cells.map { c ->
                    JSONObject()
                        .put("type", c.type)
                        .put("col", c.col)
                        .put("row", c.row)
                        .put("colSpan", c.colSpan)
                        .put("rowSpan", c.rowSpan)
                }))
        }))
        .toString(2)

    companion object {
        const val ASSET = "dashboard_grid.json"
        private const val TAG = "DashboardSpec"

        /** The saved, drag-edited spec if there is one, else the defaults from assets. */
        fun load(context: Context): DashboardSpec {
            val saved = savedFile(context)
            val defaults = loadDefault(context)
            if (saved.exists()) {
                try {
                    val spec = parse(saved.readText())
                    // A layout saved before a widget was added would hide it, so start over
                    if (spec.widgetTypes.containsAll(defaults.widgetTypes)) return spec
                    Log.i(TAG, "Saved grid predates new widgets; using defaults")
                } catch (e: JSONException) {
                    Log.w(TAG, "Ignoring unreadable saved grid", e)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Ignoring unreadable saved grid", e)
                }
            }
            return defaults
        }

        fun loadDefault(context: Context): DashboardSpec =
            parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })

        fun clearSaved(context: Context) {
            savedFile(context).delete()
        }

        private fun savedFile(context: Context) = File(context.filesDir, ASSET)

        fun parse(json: String): DashboardSpec {
            val root = JSONObject(json)
            val layouts = root.getJSONArray("layouts").objects().map { l ->
                GridLayoutSpec(
                    name = l.optString("name"),
                    minWidthDp = l.optDouble("minWidth", 0.0).toFloat(),
                    columns = l.getJSONArray("columns").floats(),
                    rows = l.getJSONArray("rows").floats(),
                    cells = l.getJSONArray("widgets").objects().mapTo(ArrayList()) { w ->
                        GridCell(
                            type = w.getString("type"),
                            col = w.getInt("col"),
                            row = w.getInt("row"),
                            colSpan = w.optInt("colSpan", 1),
                            rowSpan = w.optInt("rowSpan", 1),
                        )
                    },
                )
            }
            require(layouts.isNotEmpty()) { "$ASSET needs at least one layout" }
            return DashboardSpec(root.optDouble("gap", 16.0).toFloat(), layouts)
        }

        private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
        private fun JSONArray.floats() = FloatArray(length()) { getDouble(it).toFloat() }
    }
}

/** Column and row sizes are relative weights, like layout_weight. Cells change in edit mode. */
data class GridLayoutSpec(
    val name: String,
    val minWidthDp: Float,
    val columns: FloatArray,
    val rows: FloatArray,
    val cells: MutableList<GridCell>,
) {
    fun contains(c: GridCell) =
        c.col >= 0 && c.row >= 0 && c.col + c.colSpan <= columns.size && c.row + c.rowSpan <= rows.size
}

data class GridCell(val type: String, val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int) {
    fun overlaps(o: GridCell) =
        col < o.col + o.colSpan && o.col < col + colSpan && row < o.row + o.rowSpan && o.row < row + rowSpan
}
