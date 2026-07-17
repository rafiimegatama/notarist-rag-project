# 04 — UI Guidelines

## Every screen handles six states

A list/detail screen must render, in order of precedence:

1. **Loading** — `LoadingSkeleton` / `SkeletonList` / `LoadingState` (never a bare spinner on first load).
2. **Error** — `ErrorState` with `onRetry` (only when there is no cached data to show).
3. **Offline** — `OfflineBanner` above content when `offline` is true (data may be stale, not absent).
4. **Mock** — `MockBanner entity="…"` whenever the backing service `usingMock` is true. Honesty rule:
   never present fixtures as real server data.
5. **Empty** — `EmptyState` with icon/title/description and an optional action.
6. **Success feedback** — `Alert`/`SuccessCheck`/toast after a mutation (approve, submit, delete).

## Standard building blocks

- **Text**: `<AppText variant color>`; variants map to typography tokens. No raw `<Text>`.
- **Buttons**: `PrimaryButton` (main CTA), `SecondaryButton` (outline), `DangerButton` (destructive).
  Handle `loading`/`disabled`; never hand-roll a `TouchableOpacity` button.
- **Chips**: `StatusChip` (generic, `tone: soft|solid|outline`), plus semantic wrappers `PriorityChip`,
  `ApprovalChip`/`ApprovalBadge`, `ConfidenceBadge`. Pass a theme color **key**, not a hex.
- **Containers**: `Screen` (safe-area + scroll + keyboard), `Card`, `SectionHeader`, `Divider`.
- **Footers**: `BottomActionBar` / `ActionFooter` / `StickyBottomAction` for pinned actions.
- **Dialogs**: `ConfirmationDialog` / `DangerDialog` for themed, labeled confirmations (prefer over
  `Alert.alert` for destructive flows — see ConversationsScreen delete).

## Workflow widgets

- Case progress → `WorkflowStepper currentIndex={caseStatusToStage(status)}`.
- Bundle pipeline → `PipelineProgress` (OCR→NER→Chunk→Embedding→Index→Completed).
- Bundle sub-status → `BundleProgress` (OCR/Verifikasi/Draft/QC/Approval).

## Copy

- UI language is Indonesian. Status labels come from `constants/workflow.js` — don't inline strings for
  a status that already has a label there.

## Don't

- Don't hardcode colors, spacing, or font sizes.
- Don't duplicate a card/row/panel that already exists — compose or extend.
- Don't show zeros/empty as if they were a successful backend response when the service is mocked.
