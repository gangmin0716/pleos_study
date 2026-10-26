package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.min

/**
 * Top-down track map: the road in light grey, this lap's progress in black (like the
 * tachometer fill), the finish line, and the car as a Pleos-blue dot.
 */
class TrackMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var outline: List<PointF>? = null
    /** Cumulative distance fraction at each outline point, to place the lap progress. */
    private var outlineFractions = FloatArray(0)
    private var trail: List<PointF> = emptyList()
    private var car: PointF? = null
    private var lapPosition: Float? = null

    private val dp = resources.displayMetrics.density
    private val roadPaint = strokePaint(R.color.pleos_track, 22 * dp)
    private val progressPaint = strokePaint(R.color.pleos_text, 6 * dp)
    private val finishPaint = strokePaint(R.color.pleos_text, 4 * dp).apply { strokeCap = Paint.Cap.BUTT }
    private val carRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        // Close to Pleos Shadow/Light/Level2
        setShadowLayer(8 * resources.displayMetrics.density, 0f, 2 * resources.displayMetrics.density, 0x29000000)
    }
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.pleos_blue) }
    private val path = Path()

    // World -> screen transform, rebuilt whenever the shape or size changes
    private var rotated = false
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setOutline(points: List<PointF>?) {
        outline = points
        outlineFractions = points?.let(::cumulativeFractions) ?: FloatArray(0)
        invalidate()
    }

    fun update(trail: List<PointF>, carX: Float?, carZ: Float?, lapPosition: Float?) {
        this.trail = trail
        car = if (carX != null && carZ != null) PointF(carX, carZ) else null
        this.lapPosition = lapPosition
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val shape = outline ?: (trail + listOfNotNull(car))
        if (shape.size < 2) {
            car?.let { drawCar(canvas, width / 2f, height / 2f) }
            return
        }
        fit(shape)

        val closed = outline != null
        tracePath(shape, shape.size, closed)
        canvas.drawPath(path, roadPaint)

        val outline = outline
        val position = lapPosition
        if (outline != null && position != null) {
            val end = outlineFractions.indexOfFirst { it > position }.let { if (it < 0) outline.size else it }
            if (end >= 2) {
                tracePath(outline, end, closed = false)
                canvas.drawPath(path, progressPaint)
            }
        } else if (outline == null) {
            tracePath(trail, trail.size, closed = false)
            canvas.drawPath(path, progressPaint)
        }

        if (outline != null) drawFinishLine(canvas, outline)
        car?.let { drawCar(canvas, sx(it), sy(it)) }
    }

    private fun drawFinishLine(canvas: Canvas, outline: List<PointF>) {
        val a = outline[0]
        val b = outline[min(3, outline.size - 1)]
        val dx = sx(b) - sx(a)
        val dy = sy(b) - sy(a)
        val len = hypot(dx, dy).takeIf { it > 0f } ?: return
        val half = 20 * dp
        val nx = -dy / len * half
        val ny = dx / len * half
        canvas.drawLine(sx(a) - nx, sy(a) - ny, sx(a) + nx, sy(a) + ny, finishPaint)
    }

    private fun drawCar(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 15 * dp, carRingPaint)
        canvas.drawCircle(x, y, 11 * dp, carPaint)
    }

    private fun tracePath(points: List<PointF>, count: Int, closed: Boolean) {
        path.rewind()
        for (i in 0 until count) {
            val p = points[i]
            if (i == 0) path.moveTo(sx(p), sy(p)) else path.lineTo(sx(p), sy(p))
        }
        if (closed) path.close()
    }

    /** Scales the shape to fill the view, turning it 90° when that makes it bigger. */
    private fun fit(points: List<PointF>) {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (p in points) {
            minX = min(minX, p.x); maxX = maxOf(maxX, p.x)
            minZ = min(minZ, p.y); maxZ = maxOf(maxZ, p.y)
        }
        val pad = 40 * dp
        val w = (width - 2 * pad).coerceAtLeast(1f)
        val h = (height - 2 * pad).coerceAtLeast(1f)
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanZ = (maxZ - minZ).coerceAtLeast(1f)
        val upright = min(w / spanX, h / spanZ)
        val turned = min(w / spanZ, h / spanX)
        rotated = turned > upright * 1.05f
        scale = if (rotated) turned else upright

        // Centre the bounding box
        val cx = (minX + maxX) / 2
        val cz = (minZ + maxZ) / 2
        offsetX = 0f
        offsetY = 0f
        val center = PointF(cx, cz)
        offsetX = width / 2f - sx(center)
        offsetY = height / 2f - sy(center)
    }

    // Rotating turns world +x into screen down and world +z into screen right
    private fun sx(p: PointF) = (if (rotated) p.y else p.x) * scale + offsetX
    private fun sy(p: PointF) = (if (rotated) -p.x else p.y) * scale + offsetY

    private fun strokePaint(colorId: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, colorId)
    }

    private fun cumulativeFractions(points: List<PointF>): FloatArray {
        val out = FloatArray(points.size)
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
            out[i] = total
        }
        if (total > 0f) for (i in out.indices) out[i] /= total
        return out
    }
}
