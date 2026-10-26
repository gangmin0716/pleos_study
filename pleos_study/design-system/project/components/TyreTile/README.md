# TyreTile

Four tiles, front-left, front-right, rear-left, rear-right, each showing a tyre's core temperature.

- Working window 75–100 °C: `track` fill. Below: `ac-cold`. Above: `ac-hot`. The temperature stays `text-primary` in every state; the fill carries the state, the number carries the value.
- `body-large` Bold, tabular, with a degree sign and no unit letter. Tiles use `radius-icon-button` and an 8px gap (`spacing-03`), and share the card's height equally.
- The consumer provides four temperatures in °C (`tyreCoreTemp`).
