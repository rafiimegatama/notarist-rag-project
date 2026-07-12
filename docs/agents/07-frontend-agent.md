# 07 — Frontend Agent

## Stack

React Native via Expo (`frontend/NotaristApp`). Per the STEP 5 architecture decision:
TypeScript was the intended target, though the current scaffold (`Add React Native frontend
(Expo)`) is plain JavaScript (`.js`) — reconcile this with [[05-architect-agent]] before
assuming TS is already in force; don't silently introduce `.tsx` alongside `.js` without a
decision either way.

## Structure

```
src/api/          — HTTP client + one file per backend module surface
                    (auth.js, documents.js, assistant.js, client.js)
src/contexts/      — React context providers (AuthContext.js)
src/screens/       — HomeScreen, LoginScreen, DocumentsScreen, AssistantScreen
src/navigation/    — navigator setup
src/components/    — shared UI components
src/hooks/         — custom hooks
src/utils/         — helpers
```

Per STEP 5: navigation is Bottom Tab (5 tabs) + Modal Stack; 15 screens + 5 modals were
planned in total — the current 4-screen scaffold is an early slice, not the full plan.
Server state via React Query, local state via Zustand (per STEP 5 decision) — verify these
are actually wired before assuming; if not present yet, that's a gap against the frozen
frontend architecture, not a new decision to make ad hoc.

## SSE / streaming

The AI assistant's citation-first, token-streaming responses (see [[02-architecture]]) are
consumed via SSE. Per STEP 5, this is a custom `EventSource` polyfill (`fetch` +
`ReadableStream`), since React Native has no native `EventSource`. Don't reach for a
third-party SSE library without checking whether the polyfill decision still holds.

## Conventions

- One API module per backend capability (`api/auth.js` ↔ `notarist-auth`, `api/documents.js`
  ↔ `notarist-document`, `api/assistant.js` ↔ `notarist-assistant`) — mirrors the backend's
  module boundaries so a frontend change maps predictably to a backend module.
- Auth token lifecycle (login, refresh, logout) is centralized in `AuthContext`, not
  duplicated per screen.
- A response without a citation is a defect the frontend should surface, not silently accept
  — the citation-first contract (see [[02-architecture]]) is a UI-visible guarantee, not just
  a backend implementation detail.

## Before adding a screen or API call

Confirm the corresponding backend endpoint is part of a frozen contract (STEP 7.5 OpenAPI
structure) rather than guessing the request/response shape — cross-reference
`docs/architecture/step7_5_foundation_contracts.md` or the actual `api/rest` controller.
