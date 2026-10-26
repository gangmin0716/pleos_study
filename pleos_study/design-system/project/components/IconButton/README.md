# IconButton

Pleos `ivi_icon_button`, Basic type at Medium size: an 80px square with 20px corners holding a 56px icon. Media controls and any action shown only as an icon.

- Fill `button-basic-enabled`, pressed `button-basic-pressed`; icon `icon-primary`.
- The primary action in a group (play/pause in the music card) uses the filled variant: `button-filled-enabled` with an `on-filled` icon.
- Small size is 64px with `radius-button-small` and a 44px icon (`size-icon-small`). Pleos also has Plain (no fill until pressed) and Circle (`radius-pill`) types.
- The consumer provides the icon and an accessible label (`aria-label` / `contentDescription`) in Korean.
- Pleos ships no media icons: the play, pause and skip glyphs in the app are the app's own, drawn to the Pleos icon rules (24 grid, filled, `icon-primary`). Replace them if Pleos publishes media icons.
