// Central app configuration + feature flags. Feature flags gate UI whose backend does not yet
// exist, so screens can be built and shipped without faking server behavior.

import { Platform } from 'react-native';

export const APP = {
  name: 'Notarist',
  fullName: 'Notarist RAG Platform',
  tagline: 'Sistem Manajemen Dokumen Notaris',
  version: '1.0.0',
  build: '1',
};

// Backend availability flags. Flip to true ONLY when the corresponding endpoint actually exists.
// Sprint-1 reality: registration and notifications have no backend yet.
export const FEATURES = {
  // Both verified absent in Sprint 6 against the 12 controllers the backend actually ships
  // (Assistant, Auth, Bundle, Case, CaseInsights, Dashboard, Document, Ingestion, OcrReview,
  // Reminder, Search, Verification). Nothing serves registration or notifications — no route, no DTO.
  registerEndpoint: false,      // no POST /auth/register
  notificationsEndpoint: false, // no GET /notifications
  // `profileEndpoint` was removed in Sprint 5 (Task 10). It was declared and never read by a single
  // line — a switch wired to nothing, which is the same failure as the old `LIVE` block: config that
  // claims a control exists. Its knowledge is worth keeping, so it is written down instead of faked:
  //
  //   There is no GET /auth/me. The profile is derived from the JWT claims + auth context
  //   (see models/User.js). If that endpoint ever ships, add a flag HERE and have api/auth read it —
  //   the flag arrives with the code that honours it, not before.

  // Sprint 2 — case-workflow backend. Each API module falls back to marked mock fixtures while its
  // flag is false. A flag being true means the endpoint exists AND this app can render its response;
  // those are two different claims, and caseEndpoint below is false because of the second one.
  //
  // ---------------------------------------------------------------------------
  // caseEndpoint — GET /api/v1/cases EXISTS and returns CaseResponse.
  //
  // Sprint 6.5 removed the RENDERING blocker. The paragraph here used to say CaseCard leads every row
  // with item.debtorName over item.bank, so a live list would read "— · —" on every row: the Case
  // aggregate models no debtor, no bank and no collateral (grep the case domain — still nothing), and
  // `notaris` exists only as assignedNotarisId, a UUID rather than a name.
  //
  // CaseCard and CaseHeader now lead with `caseNumber` — the one identifying field BOTH the fixtures
  // and CaseResponse carry — and render caseType/nomorAkta beneath it, dropping nulls rather than
  // printing placeholder dashes. The debtor/bank/collateral fields survive as detail rows on
  // CaseDetailScreen, populated from fixtures and "—" against the live endpoint. So a live case list
  // is now identifiable and workable by case number.
  //
  // Sprint 7 VERIFIED IT RUNNING and turned it on. Against a real backend + real PostgreSQL, with
  // real cases created through POST /cases, the live response feeds the real normalizers and renders:
  //
  //   GET /cases -> 200 {items:[…], page:{number,size,totalElements,totalPages,hasNext,…}}
  //   rows       -> primary "14/VI/2026", detail "AJB"     (identifiable, no placeholder dashes)
  //   GET /cases/{uuid}          -> 200 CaseResponse
  //   GET /cases/{uuid}/timeline -> 200, entries render via occurredAt
  //
  // Getting there took two BACKEND fixes (a live endpoint is the only thing that could have found
  // them; both are invisible to javac and to the module's own tests) — see FIXES.txt in the runbook:
  //   · CaseJpaRepository.search/countBySearch: `:createdFrom IS NULL OR …` on an untyped null
  //     Instant made PostgreSQL fail with 42P18 "could not determine data type of parameter $10".
  //     Every GET /cases was a 500. The date filters are now CAST(:p AS timestamp).
  //   · NotaristApplication had no @EnableJpaRepositories, so no JPA repository outside
  //     com.notarist.web was ever found and the app could not start at all.
  //
  // Known and DELIBERATE, not a blocker: CaseController.listCases still has no free-text param, so
  // the search box cannot filter server-side. api/cases.js surfaces that via `unsupportedFilters` and
  // CaseListScreen banners it rather than lying — degraded honestly.
  //
  // Known and cosmetic: a freshly opened case is CASE_CREATED, which STATE_TO_UI_STATUS maps to null
  // by design, so its chip reads "—" until it reaches WAITING_VERIFICATION. Real state, no UI bucket.
  caseEndpoint: true,

  // ---------------------------------------------------------------------------
  // bundleEndpoint — ON since Sprint 7, verified against the running backend. The UUID argument that
  // kept it false is gone: with caseEndpoint on, bundle ids now come from real CaseResponses, not
  // from 'bnd-001'. Exercised end to end against real PostgreSQL:
  //
  //   POST /cases/{uuid}/bundles     -> 201 BundleResponse (real bundleId)
  //   GET  /cases/{uuid}/bundles     -> 200 [BundleResponse]   (a bare ARRAY, not a page)
  //   GET  /bundles/{uuid}           -> 200 BundleResponse
  //   GET  /bundles/{uuid}/timeline  -> 200, entries render via occurredAt
  //
  // normalizeBundle handles the real shape as written: BundleResponse has no `name` (-> null, the
  // card falls back), and the per-stage statuses are projected from `workflowStatus`.
  //
  // STILL MISSING, and deliberately not hidden: nothing lists a bundle's DOCUMENTS
  // (api/bundles#getBundleDocuments throws UNAVAILABLE rather than 404ing or guessing). BundleScreen
  // scopes that failure to the documents section, so bundle detail + timeline still render. That is a
  // backend gap — `GET /bundles/{bundleId}/documents`, or a bundleId filter on GET /documents — not a
  // reason to keep the whole bundle screen on fixtures.
  bundleEndpoint: true,

  // dashboardEndpoint — composed from /cases/statistics + /dashboard/summary + /reminders rather
  // than from /dashboard/summary alone, whose buckets are too coarse to answer the cards. The
  // reasoning is in api/dashboard.js; read it before changing the source.
  dashboardEndpoint: true,

  // reminderEndpoint — GET /api/v1/reminders. The response is bucketed, not a flat list; api/
  // reminders.js flattens it. EXPIRED_NPWP / EXPIRED_NIB reminders have no backend producer and so
  // never arrive.
  reminderEndpoint: true,

  // ---------------------------------------------------------------------------
  // Sprint 7 took the UUID excuse away — caseEndpoint and bundleEndpoint are on, so both of these are
  // now callable with REAL ids. They were tried against the running backend with real ids, and both
  // stay false for a NEW and much harder reason, found only by running them:
  //
  //   GET /bundles/{real-uuid}/verification -> 404 VERIFICATION_NOT_FOUND
  //   GET /documents/{real-uuid}/ocr        -> 404 OCR_REVIEW_NOT_FOUND
  //
  // Neither aggregate is ever PROVISIONED. The use cases that would create them —
  // VerificationProvisioningUseCase.initializeVerification and OcrReviewProvisioningUseCase — are
  // implemented and wired to NOTHING: no controller exposes them, no event listener calls them, no
  // scheduler runs them. Outside their own module they are referenced only by tests (grep the backend
  // for initializeVerification: application service + tests, nothing else). So there is no sequence of
  // HTTP calls a client can make that causes a Verification or an OcrReview to exist, and these two
  // endpoints answer 404 forever.
  //
  // This is NOT a frontend blocker and cannot be fixed here. The app's code for both is correct and
  // waiting: the screens read the real checklist / real OCR fields and post per ITEM and per FIELD id.
  // What the backend owes is a provisioning trigger — most likely on the bundle status transition
  // that means "ready to verify", and on OCR pipeline completion for the review.
  //
  // ocrReviewEndpoint additionally needs the ingestion pipeline (GCS bucket + a PaddleOCR service) to
  // produce OCR output at all; neither runs in the local stack, so even a provisioned review would
  // have no fields to show.
  ocrReviewEndpoint: false,     // route exists; NO provisioning path -> 404 forever. Backend blocker.
  verificationEndpoint: false,  // route exists; NO provisioning path -> 404 forever. Backend blocker.

  // conversationListEndpoint — genuinely absent. AssistantController serves /assistant/ask,
  // /assistant/ask/stream and /assistant/history/{sessionId}; there is no "list all conversations"
  // and no delete. Verified: scripts/validate-integration.js reports both calls as planned-endpoint.
  conversationListEndpoint: false,

  // Endpoints that are live today. These were the `LIVE` block — config nothing read, so these calls
  // were ungated. Now they are real flags, honoured by their api modules, and can be switched off to
  // fall back to fixtures like any other endpoint.
  searchEndpoint: true,      // POST /search (SearchController)
  documentsEndpoint: true,   // GET /documents, /documents/{id} (DocumentController)
  assistantEndpoint: true,   // POST /assistant/ask, GET /assistant/history/{sessionId}
  // assistantStreamEndpoint — POST /assistant/ask/stream (SSE). Served by AssistantController and
  // verified present by validate-integration (it was the "unused endpoint · POST /assistant/ask/stream"
  // note). The Assistant screen streams through api/assistantStream when this is on and falls back to
  // the synchronous assistantEndpoint when it is off OR when a stream fails to start — so turning this
  // off degrades to the whole-answer path, never to a fabricated one.
  assistantStreamEndpoint: true,
  ingestEndpoint: true,      // POST /ingest… (IngestionController)

  // Developer-only: unlocks the Component Playground screen (a pure-UI showcase, no backend). Ships
  // false; a developer flips it locally to browse every reusable component in all its states.
  devPlayground: false,
};

// ---------------------------------------------------------------------------------------------
// Sprint 5, Task 10 — ONE flag per endpoint.
//
// `LIVE` used to live here: a SECOND switching mechanism listing documents/search/assistantAsk/
// ingest as "endpoints that DO exist". It was dead config — grep proved not one line of the app ever
// read it — so those four endpoints were in fact ungated: api/search.js and api/assistant.js call
// the backend unconditionally, no flag, no mock path. The flag said one thing, the code did another.
//
// Two mechanisms cannot both be "exactly ONE flag", and a mechanism nothing reads is worse than none
// — it documents a control that does not exist. LIVE is gone; its four endpoints are FEATURES flags
// below, shipped `true` (they are genuinely live), and now actually read by their api modules.
//
// The rule: every endpoint answers to exactly one FEATURES flag, and flipping it is the whole
// integration step. No code edit, no second switch to remember.
// ---------------------------------------------------------------------------------------------

export const LINKS = {
  privacyPolicy: 'https://notarist.example/privacy',
  terms: 'https://notarist.example/terms',
  support: 'mailto:support@notarist.example',
};

export const PLATFORM_LABEL = `${Platform.OS}${Platform.Version ? ' ' + Platform.Version : ''}`;
