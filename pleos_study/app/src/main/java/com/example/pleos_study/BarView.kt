package com.example.pleos_study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Level bar (pedals, fuel, media progress) in the Pleos slider_track style: a fully rounded
 * track with the fill clipped to it. Fills left to right, or bottom to top when taller than wide.
 */
class BarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var value = 0f
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.pleos_track) }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.pleos_text) }
    private val bounds = RectF()
    private val clip = Path()

    var fillColor: Int
        get() = fillPaint.color
        set(c) {
            fillPaint.color = c
            invalidate()
        }

    var trackColor: Int
        get() = trackPaint.color
        set(c) {
            trackPaint.color = c
            invalidate()
        }

    fun update(fraction: Float) {
        value = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val r = min(w, h) / 2f
        clip.rewind()
        clip.addRoundRect(bounds, r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(clip, trackPaint)
        canvas.save()
        canvas.clipPath(clip)
        if (height > width) {
            canvas.drawRect(0f, height * (1 - value), width.toFloat(), height.toFloat(), fillPaint)
        } else {
            canvas.drawRect(0f, 0f, width * value, height.toFloat(), fillPaint)
        }
        canvas.restore()
    }
}
