# LapDelta

The difference between the last lap and the best lap, beside 최근 랩, in motorsport timing colours.

- Slower: `+s.mmm` in `ac-brake`. Equal: `±0.000` in `ac-accent`. Faster (a new personal best): `−s.mmm` in `text-primary` with an `ac-ok` dot in front, because the green alone is too faint to read (2.4:1). A session-best time is set in `ac-fastest`.
- `label-small` Bold, tabular figures, 16px before the lap time it qualifies.
- Lap times are always `m:ss.mmm`; a missing time is `--`.
- The consumer provides last and best lap in milliseconds.
