# Telltales

Warning lights from the Pleos driving view, each in its own source colour (amber `#ff8a00` for TCS and ABS, red for brake warning and seatbelt, orange EPB). Never recolour an active telltale; an inactive one is shown in `basic-200`.

- `telltale_tcs` — traction control intervening (`isTcInAction`).
- `telltale_abs` — ABS intervening (`isAbsInAction`).
- `telltale_brake_warning`, `telltale_epb`, `telltale_seatbelt` — available, not used by the dashboard yet.
