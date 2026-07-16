# 07 — Responsive Layout

## `useResponsive()` (`src/hooks/useResponsive.js`)

Derives layout flags from the live window size (recomputes on rotation / multitasking resize):

```js
const { width, height, isLandscape, isTablet, isLarge, splitView, columns } = useResponsive();
```

Breakpoints (dp): `phone < 600 (md) < 900 (lg)`.

| Field | Meaning |
|---|---|
| `isTablet` | width ≥ 600 |
| `isLarge` | width ≥ 900 |
| `isLandscape` | width > height |
| `splitView` | large screen, or landscape ≥ 720 — render two panes |
| `columns` | 1 (phone) / 2 (tablet) / 3 (large) grid hint |

## Split-view support

`OcrReviewScreen` uses `splitView` to switch between:
- **phone / portrait** — stacked: document preview above, extracted-field list below (single scroll).
- **tablet / landscape** — side-by-side: preview pinned left, scrollable field/authority panel right.

Both render from the same `leftPane` / `rightPane` nodes, so behavior is identical; only the container
flips. New split screens should follow this pattern rather than branching component trees.

## Landscape & rotation

`useWindowDimensions()` under the hood means every consumer re-renders on rotation. Nothing is locked
to portrait. Grids that use `columns` reflow automatically.

## Grids

Dashboard stat cards and playground rows use flex-wrap with `width: '48%'` / `'31%'` + `flexGrow`, so
they fill available width without hardcoded per-device breakpoints. For denser tablet grids, read
`columns` from `useResponsive()`.

## Safe areas

`Screen` applies safe-area insets per edge; `BottomActionBar` adds bottom-inset padding so pinned
actions clear the home indicator in any orientation.
