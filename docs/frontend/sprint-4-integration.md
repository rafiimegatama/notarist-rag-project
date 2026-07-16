# Sprint 4 — Replacing mocks with the real backend

What got wired to the live API, what did not, and why. Verified 2026-07-15 by running the api layer
against payloads shaped from the Java DTOs.

## Integrated

| Endpoint | Consumer | Notes |
|---|---|---|
| `GET /api/v1/cases/statistics` | `api/dashboard.js` | The real source of every per-state counter |
| `GET /api/v1/dashboard/summary` | `api/dashboard.js` | `totalCases` only — see below |
| `GET /api/v1/reminders` | `api/reminders.js` | Buckets flattened to a list |

`FEATURES.dashboardEndpoint` and `FEATURES.reminderEndpoint` are now `true`.

## Still mocked

| Endpoint | Flag | Why |
|---|---|---|
| `GET /api/v1/cases`, `/cases/{id}` | `caseEndpoint: false` | **Backend gap** — see below |
| `GET /api/v1/cases/{id}/timeline` | `caseEndpoint: false` | Keyed by case ID; blocked behind the case list |
| `GET /api/v1/cases/{id}/activities` | — | Not consumed by any screen yet |
| `/bundles`, `/ocr/{id}/fields`, `/verification`, `/auth/register`, `/notifications` | various | No backend |

## The dashboard is three calls, not one

`GET /dashboard/summary` looks like the obvious source and cannot answer the UI.
`CaseAnalyticsService.bucketOf()` rolls states up:

```
WAITING_NOTARY_APPROVAL, FINALIZED   -> approved
WAITING_VERIFICATION,    VERIFIED    -> verification
WAITING_QC, QC_FAILED, QC_APPROVED   -> qc
```

The dashboard shows **Menunggu Approval** and **Siap Kirim** as two separate cards. `summary.approved`
has already added them together. Using it for either would double-count the other — a number that
looks right and is wrong.

`/cases/statistics` returns `statusCounts` keyed by the exact `CaseState`, which maps 1:1 onto each
card. Verified against a payload where `approved` = 17:

| Card | Source | Value | `summary` would have said |
|---|---|---|---|
| Menunggu Approval | `statusCounts.WAITING_NOTARY_APPROVAL` | 8 | 17 |
| Siap Kirim | `statusCounts.FINALIZED` | 9 | 17 |
| Menunggu Verifikasi | `statusCounts.WAITING_VERIFICATION` | 7 | 11 (incl. VERIFIED) |

So: `statistics` for the counters, `summary` for `totalCases`, `reminders` for `reminderCount` and
`overdueSkmht`. Three calls in parallel; every number traceable to one server field.

## Known backend gaps

### 1. The Case domain has no debtor, bank, or collateral — cases cannot be integrated

`GET /api/v1/cases` exists and returns `CaseResponse`. It is **not renderable**. The `Case` aggregate
models none of the fields the UI is built around:

| UI field | Rendered at | Backend |
|---|---|---|
| `debtorName` | `CaseCard.js:32` — **primary line of every row** | absent from the domain |
| `bank` | `CaseCard.js:34` subtitle | absent from the domain |
| `collateralType` | `CaseDetailScreen.js:73` | absent from the domain |
| `notaris` | `CaseHeader` | only `assignedNotarisId`, a UUID |

`grep -riE 'debtor|debitur|bank|collateral|jaminan'` over the case domain returns **nothing**. This
is not a DTO that forgot a field — the concept was never modeled.

Flipping the flag does worse than blank the cards. `normalizeCase` reads `raw.status`, the server
sends `state`, and the default is `'DRAFT'` — so every case would render **"Tanpa Nama · — · DRAFT"**.
Confidently wrong, which is harder to notice than visibly broken.

**Needs a domain change** (Claude 1). Until then `caseEndpoint` stays `false`.

### 2. Timeline is coupled to the case list

`/cases/{id}/timeline` works, but a timeline is addressed *by case ID*. While cases are fixtures the
only IDs the UI can supply are `'case-001'`, which no backend has heard of — every call would 404.
It shares `caseEndpoint` deliberately; a separate flag would let someone enable it alone and get a
screen of 404s.

### 3. Two reminder types have no producer

The UI knows `EXPIRED_NPWP` and `EXPIRED_NIB`; nothing on the backend watches identity-document
expiry. Those reminders never arrive. Left in `REMINDER_TYPE` because the gap is in the backend, and
`reminderTypeMeta()` falls back safely for unknown types.

### 4. Reminders have no ID

`ReminderResponse.ReminderItem` carries no identifier — a reminder is derived from case state, not
stored. `caseId:reminderType` is used as the React key; one case raises at most one reminder per
type, so it is stable.

### 5. Eleven case states have nowhere to show

`CASE_CREATED, UPLOADING, OCR_RUNNING, OCR_FAILED, FIELD_EXTRACTION, VERIFIED, DRAFT_FAILED,
QC_FAILED, QC_APPROVED, DELIVERED, ARCHIVED` have no dashboard card. They are counted in `totalCase`
but invisible individually. A UI gap, not a data gap — and out of scope, since this sprint was
forbidden from building screens.

`DRAFT_FAILED` is deliberately **not** folded into the Draft card: a failure needing attention is not
a draft in progress, and hiding it inside a normal-looking number is how failures go unnoticed.

## Consequence of the split you should expect

The dashboard is now real; the case list is still fixtures. The cards navigate into the case list
filtered by status (`goCases('WAITING_APPROVAL')`), so **the counters will not match the list they
link to**, and the two vocabularies differ (`WAITING_APPROVAL` vs `WAITING_NOTARY_APPROVAL`). The
list shows its "data contoh" banner, so this is legible rather than deceptive — but it resolves only
when the case list goes live.

## Graceful degradation

`is404()` in `api/_support.js` splits three cases that want different handling:

| | Meaning | Behaviour |
|---|---|---|
| **404** | endpoint not deployed here | fall back to fixtures, tagged `__mock: true` → banner |
| **offline** | no answer at all | offline state; data may be fine |
| **5xx** | route exists and broke | error state — masking an outage behind plausible numbers is worse than showing it |

`/reminders` failing does not blank the case counters (`Promise.allSettled`). When reminders are
unavailable `overdueSkmht` and `reminderCount` are **`null`, not `0`** — `0` is a claim ("nothing is
overdue"), `null` is the truth ("we do not know").

### The providers read the response, not the flag

`DashboardProvider` and `ReminderProvider` now derive `usingMock` from `isMock(data)` rather than
`Service.usingMock`. The flag says which path was *intended*; the response says which one came back.
They diverge exactly when an endpoint is enabled but 404s — and a flag-driven banner would then stay
hidden and present sample data as real. In `ReminderProvider` it also picks the clock: a stale flag
would filter live reminders against the fixtures' frozen `MOCK_NOW`.

The service-level `usingMock` is kept, because it answers a different question: "is this build wired
to a live backend?"

## Validation

| Check | Result |
|---|---|
| Parse (`@babel/parser`, JSX + ESM), 137 files | 0 errors |
| Relative imports resolve | 0 unresolved |
| Named imports exist in target | 0 missing |
| Behavioural, real source vs DTO-shaped payloads | 24/24 |

**Not run:** no ESLint or Jest in this project, and the app was never launched — no simulator, and
node here is v12 against a `>=18` engine. The behavioural check transpiles the real api modules and
stubs the HTTP client, which verifies the mapping but not the rendering. **No screen was opened
against a live backend.**
