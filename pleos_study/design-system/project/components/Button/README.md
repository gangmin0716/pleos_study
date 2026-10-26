# Button

Pleos `ivi_basic_button` at Small size: a 64px text button for screen-level actions such as 편집, 초기화, 완료.

- Basic (default): fill `button-basic-enabled`, pressed `button-basic-pressed`, label `body-small` (26/34 Bold) in `text-primary`.
- Filled: fill `button-filled-enabled`, pressed `button-filled-pressed`, label `on-filled`. Use it for the one action that ends a mode (완료 in edit mode).
- Disabled: no fill, 2px `divider-secondary` border, label `text-quaternary`.
- Height `size-button-small`, corners `radius-button-small`, horizontal padding 24px, minimum width 100px. An icon goes before the label at 28px with a `spacing-04` gap (초기화 uses `ic_refresh`).
- Medium (80px, `radius-button-medium`, 30/38 label) and Large (100px, `radius-card`, 32/40 label) exist in Pleos; the dashboard does not use them yet.
- Labels are one short Korean verb or noun. No icons-only text buttons; use IconButton.
- Focus: 3px `focus-ring` outline, 3px offset.
