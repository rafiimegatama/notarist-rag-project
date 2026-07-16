// A Bundle groups incoming documents within a Case and carries the per-stage sub-status
// (OCR / verification / draft / QC / approval) rendered as a row of chips on the Bundle screen.
//
// Sprint 5 (Task 2): rewritten against backend/notarist-case/.../api/response/BundleResponse.java.
//
// THE SHAPE MISMATCH, stated plainly, because it is the largest one in this sprint:
//
//   This UI models FIVE independent per-stage statuses (ocr, verification, draft, qc, approval).
//   The backend models ONE linear `workflowStatus` walking a 10-state machine, plus a separate
//   `assemblyStatus` (OPEN/COMPLETE/LOCKED) for document collection.
//
// Five parallel dials cannot be read off one linear position without inventing something. What CAN
// be done honestly is a projection: the pipeline is ordered, so a stage the pipeline has already
// passed is DONE, the stage it currently sits on is IN_PROGRESS, and stages ahead are PENDING. That
// is derived from the backend's own state machine, not fabricated.
//
// The exception is `draft`, left null on purpose. BundleWorkflowStatus has NO drafting state:
// drafting is a CASE-level concern (CaseState.GENERATING_DRAFT). Deriving a bundle's draft chip from
// a bundle status that knows nothing about drafting would be the "confidently wrong" failure the
// dashboard notes warn about. null renders as "—" via stepStatusMeta.
import { STEP_STATUS } from '../constants/workflow';
import { pick, str, count, isoDate, oneOf, withExtras, makeNormalizer } from './normalize';

const STEP_KEYS = Object.keys(STEP_STATUS); // PENDING | IN_PROGRESS | NEEDS_REVIEW | DONE | REJECTED

// The backend's linear pipeline, in order. Index = how far the bundle has travelled.
const WORKFLOW_ORDER = [
  'OPEN',
  'COLLECTING_DOCUMENTS',
  'READY_FOR_VERIFICATION',
  'UNDER_VERIFICATION',
  'READY_FOR_QC',
  'QC_PASSED',
  'READY_FOR_DELIVERY',
  'DELIVERED',
  'LOCKED',
];

// Where each UI stage sits on that pipeline: the index at which the stage starts being worked, and
// the index by which it is finished.
const STAGE_SPAN = {
  ocr: { start: 1, done: 2 },          // collecting/extracting -> done once ready to verify
  verification: { start: 3, done: 4 }, // UNDER_VERIFICATION -> done at READY_FOR_QC
  qc: { start: 4, done: 5 },           // READY_FOR_QC -> done at QC_PASSED
  approval: { start: 6, done: 7 },     // READY_FOR_DELIVERY -> done at DELIVERED
};

/** Project one linear workflow position onto one UI stage chip. Exported so tests can assert it. */
export function stageStatus(workflowStatus, stage) {
  const span = STAGE_SPAN[stage];
  if (!span) return null;        // `draft` has no backend equivalent (see header)

  // QC_FAILED is deliberately absent from WORKFLOW_ORDER: it is a branch off the linear path, not a
  // position on it, so it has no index to compare. It must therefore be resolved BEFORE the ordinal
  // logic — an earlier draft checked it after the index lookup, where the `idx === -1` guard had
  // already returned null and a failed QC silently rendered as "—" instead of "Ditolak".
  if (workflowStatus === 'QC_FAILED') {
    if (stage === 'qc') return 'REJECTED';
    if (stage === 'ocr' || stage === 'verification') return 'DONE'; // already cleared to reach QC
    return 'PENDING';                                               // approval not reachable yet
  }

  const idx = WORKFLOW_ORDER.indexOf(workflowStatus);
  if (idx === -1) return null;   // unknown/absent/future state -> "—", never a guess
  if (idx >= span.done) return 'DONE';
  if (idx >= span.start) return 'IN_PROGRESS';
  return 'PENDING';
}

const CONSUMED = [
  'id', 'bundleId', 'caseId', 'tenantId', 'bundleType', 'name',
  'expectedDocumentCount', 'documentCount', 'assemblyStatus', 'workflowStatus', 'terminal',
  'createdAt', 'updatedAt',
  'ocrStatus', 'verificationStatus', 'draftStatus', 'qcStatus', 'approvalStatus',
];

// Fixtures speak the UI vocabulary; the backend does not. Prefer an explicit per-stage field when
// present (mock path), otherwise project from workflowStatus (real path).
const explicitOrProjected = (raw, field, workflowStatus, stage) => {
  const explicit = oneOf(pick(raw, [field]), STEP_KEYS, null);
  if (explicit !== null) return explicit;
  return stageStatus(workflowStatus, stage);
};

export function normalizeBundle(raw = {}) {
  const rawId = pick(raw, ['bundleId', 'id']);
  const rawCaseId = pick(raw, ['caseId']);
  const workflowStatus = str(pick(raw, ['workflowStatus']), null);

  const out = {
    id: rawId === null ? null : String(rawId),
    caseId: rawCaseId === null ? null : String(rawCaseId),

    // No `name` on the wire — BundleResponse carries `bundleType` (an enum); fixtures carry `name`.
    // Neither defaults to "Bundle": a made-up label on a legal document set is worse than "—".
    name: str(pick(raw, ['name']), null),
    bundleType: str(pick(raw, ['bundleType']), null),

    documentCount: count(pick(raw, ['documentCount']), 0),
    expectedDocumentCount: count(pick(raw, ['expectedDocumentCount']), 0),

    // Both backend dials, preserved verbatim. The chips below are a lossy projection of these; a
    // screen needing the truth should read these.
    assemblyStatus: str(pick(raw, ['assemblyStatus']), null),
    workflowStatus,
    terminal: pick(raw, ['terminal']) === true,

    ocrStatus: explicitOrProjected(raw, 'ocrStatus', workflowStatus, 'ocr'),
    verificationStatus: explicitOrProjected(raw, 'verificationStatus', workflowStatus, 'verification'),
    // No backend source. Explicit-only (mock path); null against a real endpoint.
    draftStatus: oneOf(pick(raw, ['draftStatus']), STEP_KEYS, null),
    qcStatus: explicitOrProjected(raw, 'qcStatus', workflowStatus, 'qc'),
    approvalStatus: explicitOrProjected(raw, 'approvalStatus', workflowStatus, 'approval'),

    createdAt: isoDate(pick(raw, ['createdAt']), null),
    updatedAt: isoDate(pick(raw, ['updatedAt']), null) ?? isoDate(pick(raw, ['createdAt']), null),
  };
  return withExtras(out, raw, CONSUMED);
}

export const BundleNormalizer = makeNormalizer(normalizeBundle);

// Ordered stages for the bundle's chip row / progress indicator.
export const bundleStages = (b) => [
  { key: 'ocr', label: 'OCR', status: b.ocrStatus },
  { key: 'verif', label: 'Verifikasi', status: b.verificationStatus },
  { key: 'draft', label: 'Draft', status: b.draftStatus },
  { key: 'qc', label: 'QC', status: b.qcStatus },
  { key: 'approval', label: 'Approval', status: b.approvalStatus },
];
