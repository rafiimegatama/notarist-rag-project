# 02 — Component Library

All reusable UI lives in `src/components/` and is re-exported from `src/components/index.js` (the
barrel). Screens import from the barrel or by direct path. **60 components**, 0 unused, 0 duplicate,
all present in the barrel (verified by the component checker — see `09`/report).

## Layering

```
primitives  →  Screen, AppText, Button (+Primary/Secondary/Danger), Card, Divider, Avatar,
               Badge, SectionHeader, TextField
state views →  LoadingState, LoadingSkeleton, Skeleton/SkeletonList, EmptyState, ErrorState,
               Banner, OfflineBanner, MockBanner
chips/badges →  StatusChip, PriorityChip, ApprovalChip, ApprovalBadge, ConfidenceBadge
progress    →  ProgressIndicator, BundleProgress, PipelineProgress, WorkflowStepper
entity cards →  CaseCard, BundleCard, DocumentCard, ReminderCard, GeneratedDocumentCard, InfoCard,
               StatCard
headers     →  CaseHeader, BundleHeader
timelines   →  TimelineCard, DirectorTimeline, ApprovalTimeline, AuditTimeline
review      →  FieldConfidenceRow, AuthorityPanel, DocumentMetadata, ChecklistItem, ChecklistCard,
               FloatingReviewToolbar
search      →  SearchBar, SearchModeToggle, SearchFilterBar, FilterBar, CitationCard
footers     →  BottomActionBar, ActionFooter, StickyBottomAction
overlays    →  ConfirmationDialog, DangerDialog
motion      →  SuccessCheck
```

Higher layers compose lower ones — e.g. `CaseHeader → WorkflowStepper`, `BundleCard → BundleProgress`,
`ChecklistCard → ChecklistItem`, all chips/badges → `StatusChip`. The dependency graph has **85 edges**
across 60 components with **no cycles** (`scratchpad/component-graph.json`).

## Design principle: props-only

Workflow widgets are pure functions of props and know nothing about data sources:

- `WorkflowStepper({ steps=CASE_WORKFLOW, currentIndex })`
- `PipelineProgress({ stages=BUNDLE_PIPELINE, currentIndex })`
- `BundleProgress({ bundle })`, `TimelineCard({ items })`, `FieldConfidenceRow({ field, onDecision })`

This is what lets the same components render mock data today and real API data tomorrow with no change.

## Duplication eliminated this sprint

| Was duplicated inline in… | Now a component |
|---|---|
| OCR field row (OcrReviewScreen) | `FieldConfidenceRow` |
| OCR authority panel + Timeline Direksi | `AuthorityPanel`, `DirectorTimeline` |
| Verification decision row | `ChecklistItem` / `ChecklistCard` |
| Search citation card | `CitationCard` |
| Search mode toggle | `SearchModeToggle` |
| Case detail header + metadata rows | `CaseHeader`, `DocumentMetadata` |
| Bundle header + stage bar/chips | `BundleHeader`, `BundleProgress` |
| Per-screen mock/offline banners | `MockBanner`, `OfflineBanner` |

## Adding a component

1. Create `src/components/MyThing.js`, read everything from `useTheme()`.
2. Add `accessibilityRole`/`accessibilityLabel`; interactive targets ≥ `theme.touchTarget.min`.
3. Export it from `src/components/index.js`.
4. Add a state showcase to the Playground (`src/screens/dev/PlaygroundScreen.js`).
5. Run the component checker — it must be used and in the barrel.
