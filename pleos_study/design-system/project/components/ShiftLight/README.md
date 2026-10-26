# ShiftLight

Fifteen rounded segments across the top bar that fill as revs approach the shift point.

- Segments light from 70% of max rpm; the strip is full at 91%. The first 40% are `shift-stage-1` (`ac-accent`), the next 40% `shift-stage-2` (`ac-telltale`), the last 20% `shift-stage-3` (`ac-brake`). Unlit segments are `track`.
- At the shift point every segment flashes `shift-stage-3` on a 90ms period (second row of the preview). This is the only blinking element in the app.
- 16px tall, 4px corners, 6px gaps; it stretches to the width it is given. Preceded by the label "RPM" in `body-small` Bold.
- The consumer provides current and maximum rpm.
