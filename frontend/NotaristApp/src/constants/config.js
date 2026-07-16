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
  // caseEndpoint — GET /api/v1/cases EXISTS. Do not flip this to true.
  //
  // The endpoint ships and returns CaseResponse. It is not renderable: the Case aggregate models no
  // debtor, no bank and no collateral (grep the case domain for debtor|bank|collateral — nothing),
  // and CaseCard shows item.debtorName as the primary line of every row with item.bank beneath it.
  // `notaris` exists only as assignedNotarisId, a UUID rather than a name.
  //
  // Flipping it does worse than blank the cards. normalizeCase reads raw.status, the server sends
  // `state`, and the default is 'DRAFT' — so every case in the list would render as
  // "Tanpa Nama · — · DRAFT". Confidently wrong beats visibly broken at hiding a bug, which is why
  // this stays false until the domain models those fields.
  //
  // Blocked on backend/domain work, not on this app. See docs/frontend/sprint-4-integration.md.
  caseEndpoint: false,

  bundleEndpoint: false,        // no /bundles

  // dashboardEndpoint — composed from /cases/statistics + /dashboard/summary + /reminders rather
  // than from /dashboard/summary alone, whose buckets are too coarse to answer the cards. The
  // reasoning is in api/dashboard.js; read it before changing the source.
  dashboardEndpoint: true,

  // reminderEndpoint — GET /api/v1/reminders. The response is bucketed, not a flat list; api/
  // reminders.js flattens it. EXPIRED_NPWP / EXPIRED_NIB reminders have no backend producer and so
  // never arrive.
  reminderEndpoint: true,

  // ---------------------------------------------------------------------------
  // Sprint 5 re-audit against the backend source. These three said "no endpoint". That is now FALSE:
  // OcrReviewController, VerificationController and BundleController all exist and serve real DTOs
  // (OcrFieldResponse, VerificationResponse, BundleResponse). The comments below were describing a
  // backend that has since shipped.
  //
  // They stay false anyway, and the reason is the same for all three: they are addressed BY CASE ID
  // or BY BUNDLE ID, and those ids come from the case list — which is still fixtures while
  // caseEndpoint is false. A real call would carry 'case-001' and 404. The whole chain unblocks
  // together, behind caseEndpoint, not one flag at a time. See TimelineService for the same argument.
  //
  // What changed in Sprint 5 is that the NORMALIZERS now match the real DTOs (models/Ocr.js,
  // models/Verification.js, models/Bundle.js), so flipping these is now a flag flip rather than a
  // rewrite. The remaining blocker is data, not code.
  ocrReviewEndpoint: false,     // PUT /documents/{id}/ocr/fields/{fieldId} EXISTS (OcrReviewController)
  verificationEndpoint: false,  // VerificationController EXISTS: checklist + POST /checklist/{itemId}
  conversationListEndpoint: false, // genuinely absent: only /assistant/history/{sessionId} exists

  // Endpoints that are live today. These were the `LIVE` block — config nothing read, so these calls
  // were ungated. Now they are real flags, honoured by their api modules, and can be switched off to
  // fall back to fixtures like any other endpoint.
  searchEndpoint: true,      // POST /search (SearchController)
  documentsEndpoint: true,   // GET /documents, /documents/{id} (DocumentController)
  assistantEndpoint: true,   // POST /assistant/ask, GET /assistant/history/{sessionId}
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
