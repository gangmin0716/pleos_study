# GearIndicator

The gear row under the tachometer, drawn like the Pleos driving view's P R N D: the engaged gear sits in a filled rounded square, the rest are faint.

- Labels R, N, 1 … top gear, Asta Sans Bold at 62% of the cell. Idle labels `text-quaternary`; the engaged cell is filled `text-primary` with its label in `background-secondary`, corners 20% of the cell.
- Cells are square; the row is centred and shrinks cells to fit the width.
- The consumer provides the gear in Assetto Corsa's numbering (0 = R, 1 = N, 2 = first). The row grows if a higher gear arrives.
