// Case is the root entity of the workflow.
//
// Sprint 5 (Task 2): rewritten against the REAL backend contract
// (backend/notarist-case/.../api/response/CaseResponse.java), which differs from the fixtures this
// normalizer was written for. The differences are not cosmetic — three of them produce WRONG data
// rather than missing data, which is the dangerous kind:
//
//   backend sends          this used to read       result before Sprint 5
//   ---------------------  ---------------------   ------------------------------------------
//   caseId                 id ?? caseId            ok
//   state (16-value enum)  status ?? 'DRAFT'       EVERY case rendered as "DRAFT"
//   bundleIds: UUID[]      bundleCount ?? 0        every case showed "0 bundle"
//   (no debtorName)        ?? 'Tanpa Nama'         read as a real record with a blank name
//   (no updatedAt)         updatedAt ?? createdAt  ok
//
// This file now handles the real shape, so caseEndpoint is no longer blocked by the NORMALIZER.
//
// Sprint 6.5: nor is it blocked by debtor/bank/collateral any more. No client-side mapping can
// conjure those, so the CARDS stopped asking for them — CaseCard/CaseHeader lead with caseNumber and
// treat debtor/bank as optional detail. What remains is verification against a running backend; see
// the flag's note in constants/config.js.
import { caseStatusMeta } from '../constants/workflow';
import { pick, str, count, isoDate, list, withExtras, makeNormalizer } from './normalize';

// Backend CaseState (17 values) -> the UI's CASE_STATUS vocabulary.
//
// A PROJECTION, not a rename: the domain lifecycle is finer-grained than the UI's, so several states
// collapse onto one label and some have no label at all. Where a state IS labelled, the label must
// agree with api/dashboard.js#STATE_TO_COUNTER — if the dashboard counts a case as "Menunggu QC", the
// case list must label it "Menunggu QC", or the two screens contradict each other on the same data.
//
// The two maps are no longer the same SIZE, and that is not drift. CASE_CREATED and UPLOADING are
// labelled here but have no dashboard COUNTER, because a label and a card are different claims: the
// list must say what every row is, while the dashboard only offers cards for the five states worth
// chasing. Both screens still agree about every state they both speak for.
//
// Sprint 7 added the first three after caseEndpoint went live. They were "deliberately absent" while
// this app ran on fixtures, and the reasoning was sound then — but real data made it wrong. They are
// the ENTIRE lifecycle reachable without the ingestion pipeline: a case opens in CASE_CREATED, a human
// walks it to UPLOADING and OCR_RUNNING, and every state after that is a Role.SYSTEM transition driven
// by pipeline events. So on a real backend these three were not edge cases, they were every row — each
// wearing a "Tidak diketahui" chip. Naming the state a case is really in is not the "confidently
// wrong" failure the dashboard notes warn about; that failure was inventing a bucket the backend does
// not have, and all three of these are real CaseStates the server actually sends.
//
// Still deliberately absent (mapped to null): OCR_FAILED, FIELD_EXTRACTION, VERIFIED, DRAFT_FAILED,
// QC_FAILED, QC_APPROVED. Real states with nowhere to go in this UI, still surfaced as an unknown chip
// rather than guessed at. None is reachable until the pipeline runs, so none can appear before then.
const STATE_TO_UI_STATUS = {
  CASE_CREATED: 'CASE_CREATED',
  UPLOADING: 'UPLOADING',
  OCR_RUNNING: 'OCR_RUNNING',
  GENERATING_DRAFT: 'DRAFT',
  WAITING_VERIFICATION: 'WAITING_VERIFICATION',
  WAITING_QC: 'WAITING_QC',
  WAITING_NOTARY_APPROVAL: 'WAITING_APPROVAL',
  FINALIZED: 'READY_TO_SEND',
  DELIVERED: 'DELIVERED',
  ARCHIVED: 'LOCKED',
};

// The UI vocabulary itself — the mock fixtures already speak it. CASE_CREATED, UPLOADING and
// OCR_RUNNING are spelled the same as the CaseStates they project from (as WAITING_VERIFICATION always
// was), so toUiStatus returns them through the pass-through below rather than the map; same answer
// either way.
const UI_STATUSES = ['CASE_CREATED', 'UPLOADING', 'OCR_RUNNING', 'DRAFT', 'WAITING_VERIFICATION', 'WAITING_QC', 'WAITING_APPROVAL', 'READY_TO_SEND', 'DELIVERED', 'LOCKED'];

// Mirror of backend CaseState (backend/notarist-case/.../domain/state/CaseState.java), in the order
// the transition table walks them (CaseStateMachine.java). CaseController.listCases does
// `CaseState.valueOf(status)` on the raw query param, so a value outside this list is an HTTP 400 —
// not an empty result. Exported so the validator can diff it against the Java enum.
export const CASE_STATES = [
  'CASE_CREATED', 'UPLOADING', 'OCR_RUNNING', 'OCR_FAILED', 'FIELD_EXTRACTION',
  'WAITING_VERIFICATION', 'VERIFIED', 'GENERATING_DRAFT', 'DRAFT_FAILED', 'WAITING_QC',
  'QC_FAILED', 'QC_APPROVED', 'WAITING_NOTARY_APPROVAL', 'FINALIZED', 'DELIVERED',
  'ARCHIVED', 'CANCELLED',
];

/** Resolve a case status from whichever vocabulary arrived. Exported so the validator can assert it. */
export function toUiStatus(raw) {
  const s = str(pick(raw, ['state', 'status']), null);
  if (s === null) return null;
  if (UI_STATUSES.indexOf(s) !== -1) return s;   // fixtures / already projected
  return STATE_TO_UI_STATUS[s] ?? null;          // backend CaseState, or an unknown future state
}

// The INVERSE projection: a UI filter chip -> the CaseState to ask the backend for.
//
// DERIVED from STATE_TO_UI_STATUS rather than hand-written, and that is the whole point. Sprint 6
// found api/cases.js sending the UI vocabulary itself as `?status=` — so `DRAFT`, `WAITING_APPROVAL`,
// `READY_TO_SEND` and `LOCKED` (4 of the 7 chips) hit CaseState.valueOf() and 400'd. A second
// hand-maintained map would fix that once and then drift the first time someone touched either side;
// deriving it makes drift unrepresentable.
//
// It inverts cleanly only because STATE_TO_UI_STATUS is injective (7 states -> 7 distinct labels).
// assertInjective below turns that from a thing a reader must notice into a thing the module proves
// at import time: if someone maps a second CaseState onto an existing label, the inverse would
// silently pick a winner and this list-filter would quietly query the wrong state. Better to fail
// loudly in dev than to filter a notary's worklist by a state they did not choose.
function invert(map) {
  const out = {};
  Object.keys(map).forEach((state) => {
    const ui = map[state];
    if (out[ui] !== undefined) {
      throw new Error(
        `STATE_TO_UI_STATUS is no longer injective: '${ui}' is claimed by both '${out[ui]}' and ` +
        `'${state}'. The UI->CaseState filter projection cannot be derived. Decide which CaseState ` +
        `the '${ui}' chip should query and give the other its own label.`,
      );
    }
    out[ui] = state;
  });
  return out;
}

export const UI_STATUS_TO_STATE = invert(STATE_TO_UI_STATUS);

/**
 * The `?status=` value for a UI filter chip, or null when the chip cannot be expressed as a
 * CaseState. Null means DO NOT SEND the param — never send the UI literal as a fallback, which is
 * exactly the bug this replaces.
 */
export function toCaseStateFilter(uiStatus) {
  const s = str(uiStatus, null);
  if (s === null) return null;
  const state = UI_STATUS_TO_STATE[s] ?? null;
  if (state !== null) return state;
  // Already a CaseState (a deep link may carry one) — pass it through rather than dropping the filter.
  return CASE_STATES.indexOf(s) !== -1 ? s : null;
}

const CONSUMED = [
  'id', 'caseId', 'caseNumber', 'caseType', 'tenantId', 'createdBy', 'assignedNotarisId',
  'state', 'status', 'terminal', 'nomorAkta', 'bundleIds', 'bundleCount', 'createdAt', 'closedAt',
  'updatedAt', 'debtorName', 'debitur', 'bank', 'collateralType', 'notaris',
];

export function normalizeCase(raw = {}) {
  const rawId = pick(raw, ['caseId', 'id']);
  const out = {
    id: rawId === null ? null : String(rawId),
    caseNumber: str(pick(raw, ['caseNumber']), null),
    caseType: str(pick(raw, ['caseType']), null),

    // NOT defaulted to a placeholder. The backend Case aggregate models no debtor and no bank, so
    // against a real endpoint these are null and the UI must render "—". 'Tanpa Nama' looked like a
    // real record whose name happened to be blank.
    debtorName: str(pick(raw, ['debtorName', 'debitur']), null),
    bank: str(pick(raw, ['bank']), null),
    collateralType: str(pick(raw, ['collateralType']), null),

    status: toUiStatus(raw),
    // Preserved verbatim alongside the projection: `status` is lossy by design, and a future card
    // may need to know a case is OCR_FAILED rather than merely "not shown".
    state: str(pick(raw, ['state']), null),
    terminal: pick(raw, ['terminal']) === true,

    // bundleIds is the real field; bundleCount is the fixture spelling. Either yields a count.
    bundleCount: Array.isArray(raw && raw.bundleIds)
      ? raw.bundleIds.length
      : count(pick(raw, ['bundleCount']), 0),
    bundleIds: list(raw && raw.bundleIds, (id) => str(id, null)),

    nomorAkta: str(pick(raw, ['nomorAkta']), null),
    // A UUID, not a name — named `assignedNotarisId` so no screen mistakes it for something
    // renderable. Resolving it to a person needs a user lookup the backend has not exposed.
    assignedNotarisId: str(pick(raw, ['assignedNotarisId']), null),
    notaris: str(pick(raw, ['notaris']), null),

    createdAt: isoDate(pick(raw, ['createdAt']), null),
    closedAt: isoDate(pick(raw, ['closedAt']), null),
    // The backend has no updatedAt. Falling back to createdAt keeps "Diperbarui …" populated and is
    // truthful: for an unmodified case they are the same instant.
    updatedAt: isoDate(pick(raw, ['updatedAt']), null) ?? isoDate(pick(raw, ['createdAt']), null),
  };
  return withExtras(out, raw, CONSUMED);
}

export const CaseNormalizer = makeNormalizer(normalizeCase);

export const caseLabel = (c) => caseStatusMeta(c && c.status).label;
