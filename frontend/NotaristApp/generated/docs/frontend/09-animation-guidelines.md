# 09 — Animation Guidelines

Principle: **tasteful, not over-animated.** Motion communicates state change; it is never decorative
loops. All timing comes from `theme.durations` / `theme.motion` — no ad-hoc curves.

## Motion tokens

- `durations`: `fast 150 · base 250 · slow 400`
- `motion.spring`: `{ friction: 6, tension: 90 }` — emphasis pops
- `motion.timing`: `{ duration: 250 }` — fades
- `motion.layout`: LayoutAnimation duration for expand/collapse

## Where motion is used (and only here)

| Interaction | Component | Technique |
|---|---|---|
| Workflow step advances | `WorkflowStepper` | active circle spring-pops (`Animated.spring`) on `currentIndex` change |
| Pipeline progress | `PipelineProgress` | fill bar animates width to the new % (`Animated.timing`, `slow`) |
| Citation expand/collapse | `CitationCard` | `LayoutAnimation` height/opacity ease |
| Approval success | `SuccessCheck` | single checkmark spring-in, then `onDone` (no loop) |
| Card / list loading | `Skeleton` / `LoadingSkeleton` | gentle opacity shimmer loop (the one intentional loop) |
| Dialog open | `ConfirmationDialog` | `Modal animationType="fade"` |

## Rules

1. Reuse the tokens; don't invent durations or easing.
2. One gesture → at most one animation. No chained/cascading flourishes.
3. Prefer `useNativeDriver: true` (transforms/opacity). The pipeline fill animates `width`, so it
   sets `useNativeDriver: false` deliberately — the documented exception.
4. Loops are reserved for loading shimmer only.
5. Animations must be skippable/interruptible — never block input while animating.

## Reduced-motion (tracked debt)

We do not yet read the OS "reduce motion" setting. Planned: a `useReducedMotion()` hook that collapses
spring/timing to `durations.instant` when the user opts out. Until then, motion is subtle enough to be
low-risk, and nothing depends on an animation completing to become usable.
