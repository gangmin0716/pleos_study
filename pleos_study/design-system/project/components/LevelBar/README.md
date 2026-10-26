# LevelBar

A fully rounded track with a fill clipped to it, after the Pleos `slider_track`. Shows a 0–100% level: pedals, fuel, media progress.

- Track `track`; corners `radius-pill`. Horizontal bars are 6–12px tall; vertical pedal bars are 24px wide and fill bottom to top.
- Fill colour names the quantity: throttle `ac-accent`, brake `ac-brake`, fuel and media progress `text-primary`.
- Pedal bars sit in a fixed 84px column with the value (`label-small` Bold, tabular) and caption (`label-small`, `text-tertiary`) underneath. The column width is fixed so a value changing from 0% to 100% never resizes the gauge beside it.
- The consumer provides the fraction (0–1) and, for pedals, the caption. Never animate the fill; telemetry already arrives 30 times a second.
