# 06 — Feature Flag Strategy

All flags live in `src/constants/config.js`.

## `FEATURES` — capability gating

A flag is `false` until the corresponding backend endpoint genuinely exists. `false` ⇒ the service
serves marked mock fixtures and the screen shows `<MockBanner>`; `true` ⇒ the service hits the real
API. Flags:

```
registerEndpoint          notificationsEndpoint      profileEndpoint
caseEndpoint              bundleEndpoint             dashboardEndpoint
reminderEndpoint          ocrReviewEndpoint          verificationEndpoint
conversationListEndpoint  devPlayground
```

Rule: **flip a flag only when the endpoint is deployed and returns the contracted shape.** Flipping is
the entire integration step for a domain (see `05`).

## `LIVE` — endpoints already real

Documents / search / assistant-ask / ingest are live; these are not gated.

## `devPlayground` — developer-only UI

When `true`:
- `AppNavigator` registers the `Playground` route (otherwise it is never added to the navigator).
- `SettingsScreen` shows a "Developer → Component Playground" entry.

Ships `false`, so the showcase never reaches end users. This is the pattern for any future
internal-only screen: gate both the route registration and the entry point on one flag.

## Guidelines

- One flag = one capability. Don't overload.
- Read flags at module scope where possible (services capture `usingMock` once) — they are build-time
  constants, not runtime-mutable.
- Never fake data behind a `true` flag; a `true` flag asserts the backend is real.
