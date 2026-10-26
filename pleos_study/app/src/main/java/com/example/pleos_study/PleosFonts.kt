package com.example.pleos_study

import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File

/**
 * Asta Sans is the Pleos Connect system typeface (Figma typography uses it for every style);
 * fall back to 42dot Sans, then the platform font, on other systems.
 */
object PleosFonts {
    val regular: Typeface by lazy { load("Regular") ?: Typeface.DEFAULT }
    val medium: Typeface by lazy { load("Medium") ?: regular }
    val bold: Typeface by lazy { load("Bold") ?: Typeface.DEFAULT_BOLD }
    val extraBold: Typeface by lazy { load("ExtraBold") ?: bold }

    private fun load(weight: String): Typeface? =
        listOf("AstaSansOTF-$weight.otf", "42dotSansOTF-$weight.otf").firstNotNullOfOrNull { file ->
            try {
                File("/product/fonts", file).takeIf { it.canRead() }?.let(Typeface::createFromFile)
            } catch (e: RuntimeException) {
                null
            }
        }

    /** Swaps every TextView under [root] to Asta Sans, keeping bold where the layout asked for it. */
    fun applyTo(root: View) {
        when (root) {
            is TextView -> root.typeface = if (root.typeface?.isBold == true) bold else regular
            is ViewGroup -> for (i in 0 until root.childCount) applyTo(root.getChildAt(i))
        }
    }
}
