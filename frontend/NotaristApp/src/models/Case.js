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
// This file now handles the real shape, so caseEndpoint is no longer blocked by the NORMALIZER. It
// is still blocked by debtor/bank/collateral, which no client-side mapping can conjure — see the
// flag's note in constants/config.js.
import { caseStatusMeta } from '../constants/workflow';
import { pick, str, count, isoDate, list, withExtras, makeNormalizer } from './normalize';

// Backend CaseState (16 values) -> the UI's 7-value CASE_STATUS vocabulary.
//
// A PROJECTION, not a rename: the domain lifecycle is finer-grained than the UI's, so several states
// collapse onto one card and some have no card at all. This mirrors api/dashboard.js#STATE_TO_COUNTER
// exactly — if the dashboard counts a case as "Menunggu QC", the case list must label it "Menunggu
// QC", or the two screens contradict each other on the same data.
//
// Deliberately absent (mapped to null): CASE_CREATED, UPLOADING, OCR_RUNNING, OCR_FAILED,
// FIELD_EXTRACTION, VERIFIED, DRAFT_FAILED, QC_FAILED, QC_APPROVED. Real states with nowhere to go
// in this UI. null surfaces them as "—"; inventing a bucket would be the "confidently wrong" failure
// the dashboard notes warn about.
const STATE_TO_UI_STATUS = {
  GENERATING_DRAFT: 'DRAFT',
  WAITING_VERIFICATION: 'WAITING_VERIFICATION',
  WAITING_QC: 'WAITING_QC',
  WAITING_NOTARY_APPROVAL: 'WAITING_APPROVAL',
  FINALIZED: 'READY_TO_SEND',
  DELIVERED: 'DELIVERED',
  ARCHIVED: 'LOCKED',
};

// The UI vocabulary itself — the mock fixtures already speak it.
const UI_STATUSES = ['DRAFT', 'WAITING_VERIFICATION', 'WAITING_QC', 'WAITING_APPROVAL', 'READY_TO_SEND', 'DELIVERED', 'LOCKED'];

/** Resolve a case status from whichever vocabulary arrived. Exported so the validator can assert it. */
export function toUiStatus(raw) {
  const s = str(pick(raw, ['state', 'status']), null);
  if (s === null) return null;
  if (UI_STATUSES.indexOf(s) !== -1) return s;   // fixtures / already projected
  return STATE_TO_UI_STATUS[s] ?? null;          // backend CaseState, or an unknown future state
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
