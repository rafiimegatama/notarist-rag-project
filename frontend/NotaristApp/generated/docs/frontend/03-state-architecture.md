# 03 — State Architecture

State is **modular by domain**. There is deliberately no single global store ("no monster context").

## Slices (`src/state/`)

| Slice | Hook | Owns |
|---|---|---|
| `DashboardContext` | `useDashboard()` | dashboard summary counters |
| `CaseContext` | `useCases()` | case list, search/status filter, pagination |
| `BundleContext` | `useBundles()` | bundles cached per caseId |
| `ReminderContext` | `useReminders()` | reminders + time-window filter |
| `SearchContext` | `useSearch()` | query, mode, results, recent + saved |
| `ConversationContext` | `useConversations()` | conversation list + delete |

Each slice is a self-contained `Provider` + `use*()` hook in its own file. They are composed by
`AppStateProviders` (`src/state/index.js`) via `reduceRight`, and mounted **once**, around the
authenticated stack in `AppNavigator` — so unauthenticated users never mount app state.

## Conventions every slice follows

- Exposes `{ data, loading, refreshing, error, offline, usingMock, reload/refresh }`.
- `mounted` ref guards `setState` after unmount.
- Reads data through a **service** (never `api/*` directly) and takes `usingMock` from that service.
- `isOffline(err)` (from `api/_support`) distinguishes "no network" from an HTTP error, driving
  `<OfflineBanner>` vs `<ErrorState>`.
- List slices (`CaseContext`) track pagination in refs (`nextPage`, `inFlight`, `generation`) to avoid
  stale-closure double-fetches on load-more.

## Screen-local state

Detail screens that don't need cross-screen sharing (CaseDetail, Bundle, OcrReview, Verification) use
the `useAsync(fn, deps)` hook against a service directly, rather than a global slice. This keeps
transient view state out of the shared providers.

## Data flow

```
Screen / slice → Service (interface) → api/* (mock | http, chosen by FEATURES flag) → fixtures | HTTP
```

Screens depend on hooks and services only; they never import `api/*` or fixtures (except the
Playground, which reads centralized fixtures for display).
