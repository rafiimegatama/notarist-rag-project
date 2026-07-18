// bundleApi — bundles belonging to a case, plus the Human Verification sub-flow (a bundle-scoped
// concern, folded in here to keep the API surface at the 7 modules Sprint 2 specifies).
//
// Sprint 6 rewrote this module against the real controllers. It was the least accurate file in the
// app: of its four calls, ONE had the right shape. The routes, verbatim from the Java:
//
//   BundleController (@RequestMapping(API_BASE_PATH), so each path is absolute):
//     POST   /cases/{caseId}/bundles          -> ApiResponse<BundleResponse>
//     GET    /cases/{caseId}/bundles          -> ApiResponse<List<BundleResponse>>   ** BARE ARRAY **
//     GET    /bundles/{bundleId}              -> ApiResponse<BundleResponse>
//     PATCH  /bundles/{bundleId}/status       -> ApiResponse<BundleResponse>
//     GET    /bundles/{bundleId}/timeline     -> ApiResponse<BundleTimelineResponse>
//
//   VerificationController (@RequestMapping(API_BASE_PATH + "/bundles/{bundleId}/verification")):
//     GET    ""                               -> ApiResponse<VerificationResponse>
//     GET    /summary                         -> ApiResponse<VerificationSummaryResponse>
//     POST   /checklist/{itemId}              -> ApiResponse<VerificationResponse>   ** PER ITEM **
//     PATCH  /status                          -> ApiResponse<VerificationResponse>
//
// There is NO GET /bundles/{id}/documents and NO bulk POST /bundles/{id}/verification. Both were
// invented by this file. See the notes on each function.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock, requireEndpoint } from './_support';
import { ApiError, ErrorKind } from './errors';
import { unwrap, toList } from './envelope';
import { normalizeBundle } from '../models/Bundle';
import { normalizeVerification } from '../models/Verification';
import { MOCK_BUNDLES, MOCK_DOCUMENTS } from '../mocks/fixtures';

// GET /cases/{caseId}/bundles -> ApiResponse<List<BundleResponse>>
export async function listBundles(caseId) {
  if (FEATURES.bundleEndpoint) {
    const response = await client.get(`/cases/${caseId}/bundles`);
    // This read `response.data.data?.items ?? []`. The payload is a BARE ARRAY — `List<BundleResponse>`,
    // not a PageResponse — so `.items` was always undefined and `?? []` swallowed it. Every case would
    // have reported ZERO bundles, with no error, no empty state and nothing in the logs: the array was
    // fetched, parsed, and dropped one property access from the finish line. toList takes the array as
    // itself and still tolerates a paged wrapper if this route is ever paginated.
    return toList(unwrap(response, []), ['items', 'content']).map(normalizeBundle);
  }
  return mock((MOCK_BUNDLES[caseId] ?? []).map(normalizeBundle), { label: 'bundles' });
}

// GET /bundles/{bundleId} -> ApiResponse<BundleResponse>
export async function getBundle(bundleId) {
  if (FEATURES.bundleEndpoint) {
    const response = await client.get(`/bundles/${bundleId}`);
    // unwrap, not `response.data.data`: the latter throws a TypeError the moment a proxy or gateway
    // returns a body that is not the envelope.
    return normalizeBundle(unwrap(response, null));
  }
  const all = Object.values(MOCK_BUNDLES).flat();
  const found = all.find((b) => b.id === bundleId);
  return mock(found ? normalizeBundle(found) : normalizeBundle({ id: bundleId }), { label: 'bundle' });
}

/**
 * The documents in a bundle.
 *
 * THIS ENDPOINT DOES NOT EXIST. It never did. The old live path called
 * `GET /bundles/{id}/documents`, which BundleController does not serve (the five routes it does serve
 * are listed at the top of this file) — a guaranteed 404 the moment bundleEndpoint flipped.
 *
 * Nor can it be composed client-side: DocumentController's `GET /documents` filters on
 * `documentType` and `status` only. There is no bundleId param and no bundleId on
 * DocumentLegalResponse, so the app cannot tell which documents belong to this bundle. Fetching
 * /documents and showing the tenant's whole library as "this bundle's documents" would be a
 * fabrication of exactly the kind this sprint exists to remove.
 *
 * So it throws on the live path rather than 404ing or guessing. This is a BACKEND BLOCKER: it needs
 * `GET /bundles/{bundleId}/documents`, or a `bundleId` filter on GET /documents.
 *
 * Note this is no longer on the verification path — VerificationService now reads the real checklist
 * from getVerification() below, which is what the checklist was always supposed to come from.
 */
export async function getBundleDocuments(bundleId) {
  if (FEATURES.bundleEndpoint) {
    throw new ApiError({
      kind: ErrorKind.UNAVAILABLE,
      status: null,
      message: 'Daftar dokumen bundle belum tersedia.',
      diagnostic:
        'No backend route lists a bundle\'s documents. BundleController serves only ' +
        'POST|GET /cases/{caseId}/bundles, GET /bundles/{id}, PATCH /bundles/{id}/status, ' +
        'GET /bundles/{id}/timeline; GET /documents has no bundleId filter.',
      retryable: false,
    });
  }
  return mock(MOCK_DOCUMENTS[bundleId] ?? [], { label: 'bundle-docs' });
}

// --- Human Verification (bundle-scoped) ------------------------------------------------------

// GET /bundles/{bundleId}/verification -> ApiResponse<VerificationResponse>
//
// The checklist has a first-class backend owner. Until Sprint 6 the app called getBundleDocuments()
// and DERIVED a checklist from the document list — one row per document — because when that code was
// written no verification backend existed. One now does, and it models something quite different: a
// checklist ITEM is a legal check (with a category, a mandatory flag, a check type and a decision),
// not a document. A per-document checklist could not express "mandatory: verify the collateral
// certificate matches the deed" at all.
export async function getVerification(bundleId) {
  // No mock path — the rule api/documents#getOcrFields now follows too. A checklist item is a legal
  // check a notary signs off on; inventing the checks, or their decisions, is fabricating a review.
  //
  // The mock removed in Sprint 7 did not even fabricate, which was arguably worse:
  // `MOCK_CHECKLIST[bundleId] ?? { bundleId, checklist: [] }` fell through to EMPTY for every real
  // bundle UUID, so the screen told the notary this bundle had no checks to perform. "Nothing to
  // verify" and "verification is unavailable" are opposite claims and it was showing the dangerous one.
  //
  // The route EXISTS (VerificationController) and answers 404 VERIFICATION_NOT_FOUND: no Verification
  // is ever provisioned. See the flag's note in constants/config.js.
  requireEndpoint(FEATURES.verificationEndpoint, 'verification');
  const response = await client.get(`/bundles/${bundleId}/verification`);
  return normalizeVerification(unwrap(response, null));
}

/**
 * Record ONE checklist decision.
 *   POST /bundles/{bundleId}/verification/checklist/{itemId}  body: { decision, comment }
 *
 * `decision` takes the frontend vocabulary (APPROVED | REJECTED | NEEDS_CHECK) — verified in
 * UpdateChecklistItemRequest's javadoc and in DecisionTranslator, which accepts it alongside the
 * domain Decision names. So the screen's existing vocabulary needs no translation here.
 */
export async function submitChecklistItem(bundleId, itemId, { decision, comment = null } = {}) {
  // Gated with the GET above. The mock echoed the decision straight back, so "Kirim Verifikasi"
  // reported success for a legal sign-off that was never recorded anywhere. A write that pretends to
  // persist is the worst mock in this file.
  requireEndpoint(FEATURES.verificationEndpoint, 'verification');
  const response = await client.post(
    `/bundles/${bundleId}/verification/checklist/${itemId}`,
    { decision, comment },
  );
  return normalizeVerification(unwrap(response, null));
}

/**
 * Record every decision in the checklist.
 *
 * There is NO bulk endpoint. This module used to POST `/bundles/{id}/verification` with
 * `{ decisions: [...] }` — a route VerificationController does not serve in any form. The real API is
 * one POST per item, so this loops.
 *
 * SEQUENTIALLY, and that is deliberate: each POST returns the whole recomputed VerificationResponse,
 * and the last one to land is the one we keep. Firing them in parallel would race, and the response
 * we kept would be whichever request happened to finish last — a progress count that disagrees with
 * the decisions actually stored.
 *
 * Partial failure is REPORTED, not swallowed. If item 3 of 5 fails, items 1 and 2 are already
 * committed on the server; there is no transaction to roll back and pretending otherwise would leave
 * the notary believing nothing was saved. The error carries how far it got.
 *
 * @param {Array<{itemId: string, decision: string, comment?: string|null}>} decisions
 */
export async function submitVerification(bundleId, decisions = []) {
  // Gated with the rest of the verification surface. The mock this replaces returned
  // `{ accepted: decisions.length }` after a 500ms pause, so VerificationScreen announced
  // "Verifikasi Terkirim — Keputusan berhasil disimpan" for a set of legal attestations that reached
  // no server and no database. Of every mock in this module that was the one to delete first: the
  // others invent data to READ, this one confirmed a WRITE that never happened.
  requireEndpoint(FEATURES.verificationEndpoint, 'verification');
  let latest = null;
  for (let i = 0; i < decisions.length; i += 1) {
    const d = decisions[i];
    try {
      latest = await submitChecklistItem(bundleId, d.itemId, {
        decision: d.decision,
        comment: d.comment ?? null,
      });
    } catch (err) {
      throw new ApiError({
        kind: err && err.kind ? err.kind : ErrorKind.SERVER,
        status: err && err.status ? err.status : null,
        message: i === 0
          ? 'Gagal menyimpan keputusan. Tidak ada perubahan yang tersimpan.'
          : `Sebagian keputusan tersimpan (${i} dari ${decisions.length}), sisanya gagal. Muat ulang untuk melihat status terkini.`,
        diagnostic: `checklist item ${d.itemId} (index ${i}) failed; ${i} of ${decisions.length} committed`,
        retryable: true,
        cause: err,
      });
    }
  }
  return latest;
}
