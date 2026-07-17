# 01 — Design System

The Notarist app frontend is token-driven. **Every** color, space, radius, shadow, font size, icon
size, animation duration and elevation comes from a single theme object. No screen or component may
hardcode a hex color or a magic spacing number.

## Where tokens live

| Concern | File | Notes |
|---|---|---|
| Colors (2 palettes) | `src/theme/palette.js` | `darkPalette` (default) + `lightPalette`. The only place raw hex may appear. |
| Spacing, radius, typography, motion, icon size, touch target, elevation, z-index | `src/theme/tokens.js` | Mode-independent scales. |
| Theme assembly | `src/theme/index.js` | `buildTheme(mode)` merges palette + tokens, and injects the palette's `shadowColor` into the elevation presets to produce `theme.shadows`. |
| Consumption | `src/context/ThemeContext.js` → `useTheme()` | Every component reads `theme.*`. |

## Token reference

- **spacing**: `xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 24 · xxxl 32`
- **radius**: `sm 6 · md 8 · lg 12 · xl 16 · pill 999`
- **typography** sizes: `display 32 · h1 24 · h2 20 · h3 18 · body 15 · bodySm 13 · caption 12 · micro 11`; weights `400/500/600/700`
- **durations**: `instant 0 · fast 150 · base 250 · slow 400 · splashMin 1100`
- **motion**: `layout`, `spring {friction:6,tension:90}`, `timing {duration:250}` presets
- **iconSize**: `xs 12 · sm 16 · md 20 · lg 24 · xl 32 · xxl 48`
- **touchTarget**: `min 44 · comfortable 48` (WCAG 2.5.5 / iOS HIG)
- **elevation** → `theme.shadows`: `none · sm · md · lg` (geometry + palette shadow color)
- **zIndex**: `base 0 · sticky 10 · floating 50 · overlay 100 · dialog 200`

## Semantic colors

Status/priority colors are **named**, never chosen per-screen:

- surfaces: `background, surface, surfaceAlt, elevated, overlay`
- text: `text, textMuted, textFaint, textInverse`
- state: `primary, success, warning, danger, info`
- priority: `priorityHigh, priorityMedium, priorityLow`
- lines: `border, borderStrong`

Status→color mapping is centralized in `src/constants/workflow.js` (`caseStatusMeta`, `stepStatusMeta`,
`fieldStatusMeta`, `approvalStatusMeta`, `reminderTypeMeta`, `priorityMeta`). A component receives a
theme **color key** (e.g. `"success"`) and resolves it via `theme.colors[key]`.

## Theming rules

1. **Never** write a hex literal outside `palette.js`. Derived alphas (e.g. `color + '33'`) are allowed
   because they derive from a palette color.
2. Read sizes/spaces from `theme` — no numeric literals for layout constants a token covers.
3. Both light and dark must work; `useTheme()` re-renders the tree on mode change.
4. Text uses `<AppText variant=… color=…>` rather than raw `<Text>` so typography stays consistent.

## Known debt

- `LoginScreen` and a few pre-Sprint-1 screens still contain hardcoded hex (auth screens intentionally
  untouched this sprint). Tracked in the Sprint 3 report.
