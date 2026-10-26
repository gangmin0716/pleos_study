# Telltale

Driving-assist warning lights in the top-left corner of the cluster card, using the Pleos driving-view telltale icons as drawn (`assets/Telltales`).

- Active: the icon in its own Pleos colour (`ac-telltale` amber for TCS and ABS). Inactive: the same icon in `basic-200`, so the slot never jumps.
- 40px wide, 12px apart, TCS first then ABS.
- Amber is 2.4:1 on white, so a telltale is always the icon shape, never amber text.
- The consumer provides the on/off state (`isTcInAction`, `isAbsInAction` from Assetto Corsa).
- Other Pleos telltales in the asset set (brake warning, EPB, seatbelt) are red or orange by their own meaning; keep each in its source colour.
