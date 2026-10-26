package com.example.pleos_study

import android.view.ViewGroup

/** Tread, pressure, core and brake temperatures for all four corners, plus the compound. */
class TyresWidget(parent: ViewGroup) : DashboardWidget(parent, R.layout.widget_tyres) {

    private val panel: TyrePanelView = find(R.id.tyrePanel)

    override fun render(t: Telemetry) {
        val letter = t.tyreCompound?.let(::compoundLetter)
        panel.update(
            wear = t.tyreWear,
            coreTemps = t.tyreTemps,
            pressuresKpa = t.tyrePressuresPsi?.map { it * KPA_PER_PSI },
            brakeTemps = t.brakeTemps,
            compound = letter,
            compoundLabel = t.tyreCompound?.let { compoundLabel(letter, it) },
        )
    }

    /** "Medium (M)" -> "M"; a name without the short form in brackets gives its first letter. */
    private fun compoundLetter(name: String): String =
        Regex("""\(([A-Za-z]{1,2})\)""").find(name)?.groupValues?.get(1)?.uppercase()
            ?: name.trim().take(1).uppercase()

    private fun compoundLabel(letter: String?, name: String): String {
        val id = when (letter) {
            "S" -> R.string.tyre_compound_soft
            "M" -> R.string.tyre_compound_medium
            "H" -> R.string.tyre_compound_hard
            "W" -> R.string.tyre_compound_wet
            "I" -> R.string.tyre_compound_intermediate
            else -> return name
        }
        return context.getString(id)
    }

    companion object {
        private const val KPA_PER_PSI = 6.89476f
    }
}
