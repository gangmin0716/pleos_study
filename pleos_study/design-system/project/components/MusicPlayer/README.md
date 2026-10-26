# MusicPlayer

The now-playing card, laid out like a full-screen music player: the album art, heavily blurred, is the card's ground, with large art, title, progress and transport on top. It is the one card that is not white.

- Ground: the album art scaled to 140% and blurred (60px radius), clipped to `radius-card`, under a left-to-right scrim from `alpha-dark-300` to `alpha-dark-500`. With no art the ground is a `basic-600` to `basic-800` gradient. The card keeps `shadow-level2`.
- Everything on the ground uses Pleos static/light colours in both themes: title `title-medium` in `static-light-100`, artist `label-medium` in `static-light-200`, times `label-small` in `static-light-300` with tabular figures.
- Album art: square, as tall as the card allows but at most 40% of its width, `radius-icon-button` corners, its own deep shadow. Placeholder: `alpha-light-100` tile with a music note in `static-light-400`.
- Progress: an 8px LevelBar with track `alpha-light-200` and fill `static-light-200`; elapsed time under its left end, `-remaining` under its right end, both `m:ss`.
- Transport row spans the info column: shuffle, previous, play/pause, next, repeat, spaced evenly. Glyphs are `static-light-100` with no fill (64px targets for shuffle and repeat, 80px for the middle three). Pressed shows an `alpha-light-200` circle; shuffle or repeat switched on keeps that circle; repeat-one swaps to the repeat glyph with a 1. A control the playing app doesn't support fades to 35% and stops responding.
- The consumer provides title, artist, art bitmap, duration, position and playing state, plus shuffle and repeat modes; controls send play, pause, skip, shuffle and repeat to the playing app.
- Pleos ships no media glyphs: these are Material icons (Apache 2.0) in the Pleos icon style. No lyrics, Dolby or volume controls: the dashboard has no source for them.
