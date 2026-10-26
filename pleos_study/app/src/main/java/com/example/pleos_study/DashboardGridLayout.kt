package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Places widget cards on a weighted grid described by a [DashboardSpec], and switches to a
 * narrower layout from the spec whenever its own width changes (split screen, other displays).
 *
 * In [editMode] cards can be dragged: dropping on empty cells moves a card, dropping on
 * another card swaps their slots, and dragging the bottom-right corner changes its span.
 */
class DashboardGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private var spec: DashboardSpec? = null
    private val widgetViews = LinkedHashMap<String, View>()
    private var active: GridLayoutSpec? = null
    /** Cell rectangles in px for the active layout, keyed by widget type: left, top, right, bottom. */
    private val cellBounds = HashMap<String, IntArray>()
    private var colEdges: List<Pair<Int, Int>> = emptyList()
    private var rowEdges: List<Pair<Int, Int>> = emptyList()

    /** Layout name to use regardless of width, for trying out a layout from the spec. */
    var forcedLayout: String? = null
        set(value) {
            field = value
            requestLayout()
        }

    /** Called after a drag changes the active layout. */
    var onSpecChanged: ((DashboardSpec) -> Unit)? = null

    var editMode = false
        set(value) {
            field = value
            endDrag()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val handleSize = 40 * density
    private val cardRadius = resources.getDimension(R.dimen.radius_card)
    private val blue = ContextCompat.getColor(context, R.color.pleos_blue)
    private val red = ContextCompat.getColor(context, R.color.pleos_red)
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.pleos_text_disabled)
        pathEffect = DashPathEffect(floatArrayOf(10 * density, 8 * density), 0f)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = blue
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
    private val handleMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.SQUARE
    }
    private val previewFill = Paint()
    private val previewStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
    }
    private val rect = RectF()

    // Drag state
    private enum class DragMode { MOVE, RESIZE }
    private var dragging: GridCell? = null
    private var dragMode = DragMode.MOVE
    private var downX = 0f
    private var downY = 0f
    private var grabCol = 0
    private var grabRow = 0
    /** The layout the drop would produce, or null when the drop isn't allowed. */
    private var proposal: List<GridCell>? = null
    /** Where the dragged card would land, in grid cells. */
    private var target: GridCell? = null

    init {
        setWillNotDraw(false)
    }

    fun setSpec(spec: DashboardSpec, views: Map<String, View>) {
        this.spec = spec
        removeAllViews()
        widgetViews.clear()
        widgetViews.putAll(views)
        views.values.forEach { addView(it) }
        active = null
        endDrag()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val spec = spec ?: return

        val layout = spec.layouts.firstOrNull { it.name == forcedLayout } ?: spec.layoutFor(width / density)
        if (layout !== active) {
            active = layout
            Log.d(TAG, "Using grid layout '${layout.name}' for ${width / density}dp")
        }
        computeCells(layout, width, height, (spec.gapDp * density).toInt())

        for ((type, view) in widgetViews) {
            val r = cellBounds[type]
            if (r == null) {
                view.visibility = View.GONE
                continue
            }
            view.visibility = View.VISIBLE
            view.measure(
                MeasureSpec.makeMeasureSpec(r[2] - r[0], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(r[3] - r[1], MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for ((type, view) in widgetViews) {
            val cell = cellBounds[type] ?: continue
            view.layout(cell[0], cell[1], cell[2], cell[3])
        }
    }

    private fun computeCells(layout: GridLayoutSpec, width: Int, height: Int, gap: Int) {
        colEdges = edges(layout.columns, width - paddingLeft - paddingRight, gap, paddingLeft)
        rowEdges = edges(layout.rows, height - paddingTop - paddingBottom, gap, paddingTop)
        cellBounds.clear()
        for (cell in layout.cells) {
            if (!layout.contains(cell)) {
                Log.w(TAG, "Widget '${cell.type}' is outside the ${layout.columns.size}x${layout.rows.size} grid of '${layout.name}'")
                continue
            }
            cellBounds[cell.type] = bounds(cell)
        }
    }

    private fun bounds(c: GridCell) = intArrayOf(
        colEdges[c.col].first, rowEdges[c.row].first,
        colEdges[c.col + c.colSpan - 1].second, rowEdges[c.row + c.rowSpan - 1].second,
    )

    /** Start and end px of each track, splitting [size] minus the gaps by weight. */
    private fun edges(weights: FloatArray, size: Int, gap: Int, start: Int): List<Pair<Int, Int>> {
        val free = (size - gap * (weights.size - 1)).coerceAtLeast(0)
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        var pos = start.toFloat()
        return weights.map { w ->
            val from = pos
            pos += free * w / total
            val edge = from.toInt() to pos.toInt()
            pos += gap
            edge
        }
    }

    /** Index of the track under [p], splitting each gap halfway between its neighbours. */
    private fun trackAt(edges: List<Pair<Int, Int>>, p: Float): Int {
        for (i in edges.indices.reversed()) {
            val boundary = if (i == 0) Int.MIN_VALUE.toFloat() else (edges[i - 1].second + edges[i].first) / 2f
            if (p >= boundary) return i
        }
        return 0
    }

    // ---- Drawing -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (!editMode) return
        // Empty slots show through wherever no card sits (or while one is being dragged)
        for (c in colEdges) for (r in rowEdges) {
            rect.set(c.first.toFloat(), r.first.toFloat(), c.second.toFloat(), r.second.toFloat())
            rect.inset(guidePaint.strokeWidth, guidePaint.strokeWidth)
            canvas.drawRoundRect(rect, cardRadius, cardRadius, guidePaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!editMode) return

        target?.let { t ->
            val ok = proposal != null
            val color = if (ok) blue else red
            previewFill.color = ColorUtils.setAlphaComponent(color, 60)
            previewStroke.color = color
            val b = bounds(t)
            rect.set(b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), b[3].toFloat())
            canvas.drawRoundRect(rect, cardRadius, cardRadius, previewFill)
            canvas.drawRoundRect(rect, cardRadius, cardRadius, previewStroke)
        }

        for ((type, view) in widgetViews) {
            if (view.visibility != View.VISIBLE) continue
            rect.set(view.left.toFloat(), view.top.toFloat(), view.right.toFloat(), view.bottom.toFloat())
            rect.offset(view.translationX, view.translationY)
            if (dragging?.type == type && dragMode == DragMode.RESIZE) continue
            canvas.drawRoundRect(rect, cardRadius, cardRadius, borderPaint)
            // Resize handle, tucked inside the rounded corner, with a diagonal grip mark
            val inset = cardRadius * 0.3f
            val l = rect.right - handleSize - inset
            val t = rect.bottom - handleSize - inset
            canvas.drawRoundRect(l, t, l + handleSize, t + handleSize, handleSize / 4, handleSize / 4, handlePaint)
            rect.set(l, t, l + handleSize, t + handleSize)
            val m = handleSize * 0.3f
            canvas.drawLine(l + m, rect.bottom - m, rect.right - m, t + m, handleMarkPaint)
            canvas.drawLine(rect.right - handleSize / 2, rect.bottom - m, rect.right - m, t + handleSize / 2, handleMarkPaint)
        }
    }

    // ---- Dragging ------------------------------------------------------------------------

    override fun onInterceptTouchEvent(ev: MotionEvent) = editMode

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return super.onTouchEvent(event)
        val layout = active ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startDrag(layout, event.x, event.y)
            MotionEvent.ACTION_MOVE -> dragTo(layout, event.x, event.y)
            MotionEvent.ACTION_UP -> {
                dragTo(layout, event.x, event.y)
                drop(layout)
            }
            MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun startDrag(layout: GridLayoutSpec, x: Float, y: Float) {
        val cell = cardAt(layout, x, y) ?: return
        val b = cellBounds.getValue(cell.type)
        dragging = cell
        val grip = handleSize + cardRadius * 0.3f
        dragMode = if (x > b[2] - grip && y > b[3] - grip) DragMode.RESIZE else DragMode.MOVE
        downX = x
        downY = y
        grabCol = trackAt(colEdges, x) - cell.col
        grabRow = trackAt(rowEdges, y) - cell.row
        widgetViews[cell.type]?.apply {
            translationZ = 16 * density
            if (dragMode == DragMode.MOVE) alpha = 0.85f
        }
        target = cell
        proposal = layout.cells.toList()
        invalidate()
    }

    private fun dragTo(layout: GridLayoutSpec, x: Float, y: Float) {
        val cell = dragging ?: return
        val col = trackAt(colEdges, x)
        val row = trackAt(rowEdges, y)
        when (dragMode) {
            DragMode.MOVE -> {
                widgetViews[cell.type]?.apply {
                    translationX = x - downX
                    translationY = y - downY
                }
                val moved = cell.copy(
                    col = (col - grabCol).coerceIn(0, layout.columns.size - cell.colSpan),
                    row = (row - grabRow).coerceIn(0, layout.rows.size - cell.rowSpan),
                )
                proposeMove(layout, cell, moved, cardAt(layout, x, y, except = cell.type))
            }
            DragMode.RESIZE -> {
                val resized = cell.copy(
                    colSpan = (col - cell.col + 1).coerceIn(1, layout.columns.size - cell.col),
                    rowSpan = (row - cell.row + 1).coerceIn(1, layout.rows.size - cell.row),
                )
                target = resized
                val others = layout.cells.filter { it.type != cell.type }
                proposal = if (others.none { it.overlaps(resized) }) others + resized else null
            }
        }
        invalidate()
    }

    /**
     * Finger over another card: swap slots with it. Otherwise move to the cells under the
     * dragged card if they're free.
     */
    private fun proposeMove(layout: GridLayoutSpec, cell: GridCell, moved: GridCell, hovered: GridCell?) {
        val others = layout.cells.filter { it.type != cell.type }
        if (hovered != null) {
            target = hovered
            proposal = others.map {
                if (it.type == hovered.type) {
                    hovered.copy(col = cell.col, row = cell.row, colSpan = cell.colSpan, rowSpan = cell.rowSpan)
                } else {
                    it
                }
            } + cell.copy(col = hovered.col, row = hovered.row, colSpan = hovered.colSpan, rowSpan = hovered.rowSpan)
            return
        }
        target = moved
        proposal = if (others.none { it.overlaps(moved) }) others + moved else null
    }

    private fun cardAt(layout: GridLayoutSpec, x: Float, y: Float, except: String? = null): GridCell? =
        layout.cells.firstOrNull { c ->
            if (c.type == except) return@firstOrNull false
            val b = cellBounds[c.type] ?: return@firstOrNull false
            x >= b[0] && x < b[2] && y >= b[1] && y < b[3]
        }

    private fun drop(layout: GridLayoutSpec) {
        val next = proposal
        val changed = next != null && dragging != null && next.toSet() != layout.cells.toSet()
        if (changed) {
            // Keep the order of cells stable so the saved JSON stays readable
            val byType = next!!.associateBy { it.type }
            for (i in layout.cells.indices) layout.cells[i] = byType.getValue(layout.cells[i].type)
        }
        endDrag()
        if (changed) {
            requestLayout()
            spec?.let { onSpecChanged?.invoke(it) }
        }
    }

    private fun endDrag() {
        dragging?.let { cell ->
            widgetViews[cell.type]?.apply {
                translationX = 0f
                translationY = 0f
                translationZ = 0f
                alpha = 1f
            }
        }
        dragging = null
        target = null
        proposal = null
        invalidate()
    }

    companion object {
        private const val TAG = "DashboardGrid"
    }
}
