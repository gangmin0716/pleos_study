package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All four tyres the way race engineers lay them out: a tread ring with the compound badge at
 * each corner, and between them pressure, core temperature and brake disc temperature, with
 * the fronts on top and the rears mirrored below. Scales with its size.
 */
class TyrePanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** FL, FR, RL, RR; any of them may be null when the source doesn't send it. */
    private var wear: List<Float>? = null
    private var coreTemps: List<Float>? = null
    private var pressuresKpa: List<Float>? = null
    private var brakeTemps: List<Float>? = null
    private var compound: String? = null
    private var compoundLabel: String? = null

    private val dp = resources.displayMetrics.density
    private val textColor = color(R.color.pleos_text)
    private val secondaryColor = color(R.color.pleos_text_secondary)
    private val blueColor = color(R.color.pleos_blue)
    private val orangeColor = color(R.color.pleos_orange)
    private val redColor = color(R.color.pleos_red)

    private val trackPaint = strokePaint(color(R.color.pleos_track))
    private val wearPaint = strokePaint(textColor)
    private val badgePaint = strokePaint(orangeColor)
    private val dividerPaint = Paint().apply { color = color(R.color.pleos_divider) }
    private val wearTextPaint = textPaint(PleosFonts.bold, Paint.Align.CENTER)
    private val badgeTextPaint = textPaint(PleosFonts.bold, Paint.Align.CENTER)
    private val bigPaint = textPaint(PleosFonts.bold, Paint.Align.LEFT)
    private val smallPaint = textPaint(PleosFonts.medium, Paint.Align.LEFT)
    private val compoundPaint = textPaint(PleosFonts.bold, Paint.Align.CENTER)
    private val arc = RectF()

    fun update(
        wear: List<Float>?,
        coreTemps: List<Float>?,
        pressuresKpa: List<Float>?,
        brakeTemps: List<Float>?,
        compound: String?,
        compoundLabel: String?,
    ) {
        this.wear = wear
        this.coreTemps = coreTemps
        this.pressuresKpa = pressuresKpa
        this.brakeTemps = brakeTemps
        this.compound = compound
        this.compoundLabel = compoundLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val band = maxOf(h * 0.14f, MIN_TEXT_SP * dp * 1.6f)
        val half = (h - band) / 2
        val ring = min(half * 0.9f, w * 0.24f)
        val gap = 16 * dp
        // Pressure and brake rows only when they fit at a readable size; core temperature always
        val detailed = half / 3 >= MIN_TEXT_SP * dp * 1.35f
        val rowCount = if (detailed) 3 else 1

        // Compound line between the axles
        canvas.drawRect(ring + gap, half, w - ring - gap, half + 1 * dp, dividerPaint)
        canvas.drawRect(ring + gap, half + band - 1 * dp, w - ring - gap, half + band, dividerPaint)
        compoundPaint.textSize = maxOf(band * 0.42f, MIN_TEXT_SP * dp)
        compoundPaint.color = textColor
        canvas.drawText(compoundLabel ?: "--", w / 2, half + band / 2 + compoundPaint.textSize * 0.36f, compoundPaint)

        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val top = if (front) 0f else half + band
            val cx = if (left) ring / 2 else w - ring / 2
            drawRing(canvas, cx, top + half / 2, ring, wear?.getOrNull(i))

            // Pressure on the outside edge of each axle, brakes towards the middle, core in between
            val rowH = half / rowCount
            val x = if (left) ring + gap else w - ring - gap
            val columnWidth = (w - 2 * (ring + gap)) / 2 - 8 * dp
            if (!detailed) {
                drawBig(canvas, coreTemps?.getOrNull(i), x, top, rowH, left, columnWidth)
                continue
            }
            val rows = if (front) listOf(0, 1, 2) else listOf(2, 1, 0)
            drawSmall(canvas, pressuresKpa?.getOrNull(i)?.let { "${it.roundToInt()} kPa" }, x, top + rowH * rows[0], rowH, left, columnWidth, null)
            drawBig(canvas, coreTemps?.getOrNull(i), x, top + rowH * rows[1], rowH, left, columnWidth)
            drawSmall(canvas, brakeTemps?.getOrNull(i)?.let { "${it.roundToInt()}°C" }, x, top + rowH * rows[2], rowH, left, columnWidth,
                brakeTemps?.getOrNull(i)?.let { if (it > BRAKE_HOT) redColor else textColor })
        }
        // Row dividers inside each axle block
        if (!detailed) return
        for (top in listOf(0f, half + band)) {
            for (k in 1..2) {
                val y = top + half / 3 * k
                canvas.drawRect(ring + gap, y, w - ring - gap, y + 1 * dp, dividerPaint)
            }
        }
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, size: Float, wear: Float?) {
        val stroke = size * 0.08f
        val r = size / 2 - stroke / 2
        arc.set(cx - r, cy - r, cx + r, cy + r)
        trackPaint.strokeWidth = stroke
        canvas.drawArc(arc, 0f, 360f, false, trackPaint)
        if (wear != null) {
            wearPaint.strokeWidth = stroke
            wearPaint.color = when {
                wear < WEAR_LOW -> redColor
                wear < WEAR_WORN -> orangeColor
                else -> textColor
            }
            canvas.drawArc(arc, -90f, 360f * wear.coerceIn(0f, 100f) / 100f, false, wearPaint)
        }

        wearTextPaint.textSize = maxOf(size * 0.26f, MIN_TEXT_SP * dp)
        wearTextPaint.color = textColor
        canvas.drawText(wear?.let { "${it.roundToInt()}%" } ?: "--", cx, cy + wearTextPaint.textSize * 0.2f, wearTextPaint)

        // Compound badge, coloured the way race broadcasts do (soft red, medium yellow, hard white)
        val letter = compound ?: return
        val badgeR = size * 0.085f
        val by = cy + size * 0.24f
        badgePaint.strokeWidth = size * 0.018f
        badgePaint.color = compoundColor(letter)
        canvas.drawCircle(cx, by, badgeR, badgePaint)
        badgeTextPaint.textSize = badgeR * 1.2f
        badgeTextPaint.color = badgePaint.color
        canvas.drawText(letter, cx, by + badgeTextPaint.textSize * 0.36f, badgeTextPaint)
    }

    private fun drawBig(canvas: Canvas, temp: Float?, x: Float, top: Float, rowH: Float, left: Boolean, maxWidth: Float) {
        bigPaint.color = when {
            temp == null -> secondaryColor
            temp < CORE_COLD -> blueColor
            temp > CORE_HOT -> redColor
            else -> textColor
        }
        drawText(canvas, temp?.let { "%.1f°C".format(it) } ?: "--", bigPaint, (rowH * 0.62f).coerceIn((MIN_TEXT_SP + 6) * dp, BIG_TEXT_SP * dp), x, top, rowH, left, maxWidth)
    }

    private fun drawSmall(canvas: Canvas, text: String?, x: Float, top: Float, rowH: Float, left: Boolean, maxWidth: Float, color: Int?) {
        smallPaint.color = color ?: secondaryColor
        drawText(canvas, text ?: "--", smallPaint, maxOf(rowH * 0.4f, MIN_TEXT_SP * dp), x, top, rowH, left, maxWidth)
    }

    /** Draws one cell, vertically centred in its row and shrunk if it would overrun the column. */
    private fun drawText(canvas: Canvas, text: String, paint: Paint, size: Float, x: Float, top: Float, rowH: Float, left: Boolean, maxWidth: Float) {
        paint.textSize = size
        val width = paint.measureText(text)
        if (width > maxWidth) paint.textSize = size * maxWidth / width
        paint.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        canvas.drawText(text, x, top + rowH / 2 + paint.textSize * 0.36f, paint)
    }

    private fun compoundColor(letter: String) = when (letter) {
        "S", "SS", "US", "HS" -> redColor
        "M" -> orangeColor
        "W", "I" -> blueColor
        else -> secondaryColor
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    private fun strokePaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = c
    }

    private fun textPaint(face: Typeface, align: Paint.Align) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textAlign = align
        fontFeatureSettings = "tnum"
    }

    companion object {
        /** Body/Medium, the smallest text the Pleos guide allows while driving. */
        private const val MIN_TEXT_SP = 28f
        /** Headline/Small for the core temperatures. */
        private const val BIG_TEXT_SP = 48f
        private const val CORE_COLD = 75f
        private const val CORE_HOT = 100f
        private const val BRAKE_HOT = 650f
        private const val WEAR_WORN = 70f
        private const val WEAR_LOW = 50f
    }
}
