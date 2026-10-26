# Card

The one surface of the dashboard: every widget (cluster, music, track map, laps, fuel, tyres) is a Card on `background-primary`.

- Fill `background-secondary`, corners `radius-card` (32px), shadow `shadow-level2`, padding `spacing-07` (dense cards such as the cluster use `screen-padding`, 24px).
- Header: an optional 32px Pleos icon tinted `icon-primary`, then the title in `body-small` (26/34 Bold) with `text-primary`. A count or status sits at the right end as a badge (`radius-badge`, `button-filled-enabled`, `on-filled` text).
- Rows: label in `label-medium` + `text-tertiary`, value in `body-large` + `text-primary`, tabular figures. Separate groups with a 1px `divider-secondary` line.
- Cards sit on the dashboard grid 16px apart (`spacing-05`). The consumer provides the card's grid cell; a card fills it and never sets its own size.
- Do not nest cards, add borders, or tint a whole card to signal state; put state on the element that changed (a bar, a tile, a telltale).
- The one exception is MusicPlayer, whose ground is the blurred album art; it keeps the card's radius and shadow.
