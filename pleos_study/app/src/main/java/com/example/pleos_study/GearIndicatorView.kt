package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Gear row styled after the Pleos "P R N D" selector: the active gear sits in a
 * black square, the rest are grey. Index follows AC: 0 = R, 1 = N, 2 = 1st...
 */
class GearIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var gear = 1
    private var forwardGears = 6

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.pleos_text) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = PleosFonts.bold
        textAlign = Paint.Align.CENTER
    }
    private val activeText = ContextCompat.getColor(context, R.color.white)
    private val idleText = ContextCompat.getColor(context, R.color.pleos_text_disabled)

    fun update(gear: Int) {
        if (gear == this.gear) return
        this.gear = gear
        forwardGears = maxOf(forwardGears, gear - 1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val labels = listOf("R", "N") + (1..forwardGears).map(Int::toString)
        // The engaged gear takes two cells, so it stays readable at a glance however many gears there are
        val cell = min(height / 2f, width.toFloat() / (labels.size + 1))
        val big = min(height.toFloat(), cell * 2)
        var x = (width - cell * (labels.size - 1) - big) / 2

        labels.forEachIndexed { i, label ->
            val size = if (i == gear) big else cell
            textPaint.textSize = size * 0.62f
            textPaint.color = if (i == gear) activeText else idleText
            val top = (height - size) / 2
            // Rounded like the selected gear in the Pleos driving view's P R N D
            if (i == gear) canvas.drawRoundRect(x, top, x + size, top + size, size * 0.2f, size * 0.2f, boxPaint)
            val baseline = top + size / 2 - (textPaint.ascent() + textPaint.descent()) / 2
            canvas.drawText(label, x + size / 2, baseline, textPaint)
            x += size
        }
    }
}
