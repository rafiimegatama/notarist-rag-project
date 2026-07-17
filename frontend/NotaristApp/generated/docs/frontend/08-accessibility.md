# 08 — Accessibility

## Touch targets

Interactive elements meet the 44dp floor via `theme.touchTarget.min` (WCAG 2.5.5 / iOS HIG). Applied
to `Button` (`minHeight`), the OCR reject control, checklist decision buttons, the conversation delete
button, and the floating toolbar buttons. Small controls also carry `hitSlop={theme.hitSlop}`.

## Screen-reader labels & roles

- `Button` → `accessibilityRole="button"` + `accessibilityState={{disabled, busy}}` + label from title.
- `StatusChip` → `role` is `button` when pressable, else `text`; label = chip text.
- `CaseCard`/`BundleCard`/`ReminderCard` → `role="button"` with a descriptive label
  (e.g. *"Case KPR-2026-0142, debitur Budi Santoso, Menunggu Verifikasi"*).
- `CaseHeader`/`BundleHeader` → `role="header"`.
- `WorkflowStepper`/`PipelineProgress` → `role="progressbar"` with a summary label
  (*"Alur kerja: Review, langkah 3 dari 7"*).
- `ChecklistItem` decisions → `role="radio"` + `accessibilityState={{selected}}`.
- `ConfirmationDialog` → `accessibilityViewIsModal` + `role="alert"`; scrim labeled "Tutup dialog".
- Field editors and comment inputs carry `accessibilityLabel`s naming the field.

## Dynamic type

`AppText` renders RN `<Text>`, which honors the OS font-scale by default (`allowFontScaling`), so the
whole UI scales with the user's system font size. Layouts use wrap/flex (not fixed heights) so scaled
text reflows rather than clipping.

## Keyboard navigation

On web/tablet-with-keyboard, focusable RN elements (`TouchableOpacity`, `TextInput`) participate in tab
order; `SearchBar`/field inputs use `returnKeyType` and `onSubmitEditing`. Dialogs trap interaction via
the modal scrim.

## Color & contrast

Status is never conveyed by color alone — chips pair color with a text label (and often an icon/glyph
such as ✓ / ✕ / •), so color-blind users get the state from the label.

## Gaps (tracked debt)

- No automated a11y test pass yet (needs a device/emulator; not runnable on this Node 12 box).
- Focus-order and VoiceOver/TalkBack sweeps are manual TODOs for a device lab.
