package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 270° tachometer with the speed in the middle, drawn in the flat Pleos style. */
class RpmGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var rpm = 0f
    private var maxRpm = 8000f
    private var speedKmh = 0

    private val dp = resources.displayMetrics.density
    private val textColor = color(R.color.pleos_text)
    private val secondaryColor = color(R.color.pleos_text_secondary)
    private val redColor = color(R.color.pleos_red)

    private val trackPaint = arcPaint(color(R.color.pleos_track))
    private val redZonePaint = arcPaint(color(R.color.pleos_red_tint))
    private val fillPaint = arcPaint(textColor)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
    private val labelPaint = textPaint(PleosFonts.regular)
    // Driving/Speed_kmh: Asta Sans Bold, letter spacing -3 at 112
    private val speedPaint = textPaint(PleosFonts.bold).apply {
        color = textColor
        letterSpacing = -3f / 112f
    }
    private val unitPaint = textPaint(PleosFonts.regular).apply { color = secondaryColor }
    private val rpmPaint = textPaint(PleosFonts.bold)
    private val arcRect = RectF()

    fun update(rpm: Float, maxRpm: Float, speedKmh: Int) {
        this.rpm = rpm
        this.maxRpm = maxRpm.coerceAtLeast(1000f)
        this.speedKmh = speedKmh
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // A 270° arc needs about 1.71 radii of height (the bottom 90° is open)
        val stroke = 24 * dp
        val r = min(width / 2f, height / 1.71f) - stroke / 2 - 4 * dp
        val cx = width / 2f
        val cy = (height - 1.71f * r) / 2 + r
        arcRect.set(cx - r, cy - r, cx + r, cy + r)

        trackPaint.strokeWidth = stroke
        redZonePaint.strokeWidth = stroke
        fillPaint.strokeWidth = stroke

        val redline = maxRpm * REDLINE_FRACTION
        canvas.drawArc(arcRect, START_ANGLE, SWEEP, false, trackPaint)
        canvas.drawArc(arcRect, angleOf(redline), START_ANGLE + SWEEP - angleOf(redline), false, redZonePaint)

        fillPaint.color = if (rpm >= redline) redColor else textColor
        canvas.drawArc(arcRect, START_ANGLE, angleOf(rpm.coerceIn(0f, maxRpm)) - START_ANGLE, false, fillPaint)

        drawTicks(canvas, cx, cy, r - stroke / 2 - 10 * dp, redline)

        speedPaint.textSize = r * 0.46f
        canvas.drawText(speedKmh.toString(), cx, cy + speedPaint.textSize * 0.3f, speedPaint)
        unitPaint.textSize = maxOf(r * 0.12f, MIN_TEXT_SP * dp)
        canvas.drawText("km/h", cx, cy + r * 0.32f, unitPaint)

        rpmPaint.textSize = maxOf(r * 0.12f, MIN_TEXT_SP * dp)
        rpmPaint.color = if (rpm >= redline) redColor else textColor
        canvas.drawText("%,d rpm".format(rpm.roundToInt()), cx, cy + r * 0.72f, rpmPaint)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, outer: Float, redline: Float) {
        // Numbers only when the gauge is big enough to show them at a readable size
        labelPaint.textSize = outer * 0.12f
        val labelled = labelPaint.textSize >= MIN_TEXT_SP * dp
        val steps = ceil(maxRpm / 500f).toInt()
        for (i in 0..steps) {
            val value = i * 500f
            if (value > maxRpm) break
            val major = i % 2 == 0
            val rad = Math.toRadians(angleOf(value).toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val len = if (major) 18 * dp else 9 * dp
            tickPaint.strokeWidth = if (major) 3 * dp else 2 * dp
            tickPaint.color = if (value >= redline) redColor else if (major) textColor else secondaryColor
            canvas.drawLine(cx + c * outer, cy + s * outer, cx + c * (outer - len), cy + s * (outer - len), tickPaint)
            if (major && labelled) {
                val lr = outer - len - labelPaint.textSize * 0.95f
                labelPaint.color = if (value >= redline) redColor else textColor
                canvas.drawText(
                    (i / 2).toString(),
                    cx + c * lr,
                    cy + s * lr + labelPaint.textSize * 0.35f,
                    labelPaint,
                )
            }
        }
    }

    private fun angleOf(value: Float) = START_ANGLE + SWEEP * value / maxRpm

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun arcPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = c
    }

    private fun textPaint(face: android.graphics.Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textAlign = Paint.Align.CENTER
        // Equal-width digits so changing numbers don't jitter sideways
        fontFeatureSettings = "tnum"
    }

    companion object {
        /** Body/Medium: the smallest text the Pleos guide allows while driving. */
        private const val MIN_TEXT_SP = 28f
        private const val START_ANGLE = 135f
        private const val SWEEP = 270f
        const val REDLINE_FRACTION = 0.9f
    }
}
