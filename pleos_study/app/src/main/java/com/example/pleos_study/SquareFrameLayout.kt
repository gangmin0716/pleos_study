package com.example.pleos_study

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min

/** A square as tall as it's allowed to be, but never wider than 40% of the available width. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        // Round the album art to the tile's corners
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val maxWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            height
        } else {
            (MeasureSpec.getSize(widthMeasureSpec) * MAX_WIDTH_FRACTION).toInt()
        }
        val side = MeasureSpec.makeMeasureSpec(min(height, maxWidth), MeasureSpec.EXACTLY)
        super.onMeasure(side, side)
    }

    companion object {
        private const val MAX_WIDTH_FRACTION = 0.4f
    }
}
