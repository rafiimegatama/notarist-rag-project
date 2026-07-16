# 05 — API Integration Strategy

## The seam

Screens and state slices depend **only** on services (`src/services/`), described by interface
contracts in `src/services/contracts.js`. A service delegates to an `api/*` module, which chooses a
mock fixture or a real HTTP call based on a `FEATURES` flag. Screens never learn which.

```
Screen → useX() slice → XService (interface) → api/x.js → [ FEATURES.xEndpoint ? HTTP : mock fixture ]
```

## Services

| Service | Backing endpoint | Live today? |
|---|---|---|
| `CaseService` | `/cases`, `/cases/{id}` | ✗ mock (`caseEndpoint`) |
| `BundleService` | `/cases/{id}/bundles`, `/bundles/{id}`, `/bundles/{id}/documents` | ✗ mock (`bundleEndpoint`) |
| `DashboardService` | `/dashboard/summary` | ✗ mock (`dashboardEndpoint`) |
| `ReminderService` | `/reminders` | ✗ mock (`reminderEndpoint`) |
| `TimelineService` | `/cases/{id}/timeline` | ✗ mock (`caseEndpoint`) |
| `VerificationService` | `/bundles/{id}/verification` | ✗ mock (`verificationEndpoint`) |
| `OCRService` | `/documents/{id}/ocr`, field decisions | ✗ mock (`ocrReviewEndpoint`) |
| `SearchService` | `POST /search` | ✅ **live** |
| `ConversationService` | `/assistant/conversations` (list/delete) | ✗ mock (`conversationListEndpoint`); per-session history is live |

Each service exposes `usingMock` (derived from its flag) so screens render `<MockBanner>` honestly.

## Cutover procedure (per domain) when Claude-1 ships an endpoint

1. Implement the real branch in the relevant `api/*` function (the HTTP call is already written and
   commented next to the mock branch).
2. Confirm the response is normalized to the model shape (`models/Case`, `models/Bundle`, …).
3. Flip the one `FEATURES.<x>Endpoint` flag to `true` in `constants/config.js`.
4. Done — no screen, hook, service contract, or component changes. `usingMock` flips to false and the
   MockBanner disappears automatically.

## Contract stability

`services/contracts.js` documents each method's signature (params → Promise<shape>). As long as a real
implementation honors the contract, the frontend is unaffected. Response normalization in `models/*`
tolerates missing fields, so a partial early backend won't crash a screen.

## Readiness

The frontend is **integration-ready**: every case-workflow screen already renders against the exact
service methods the backend will implement; only the transport swap remains.
