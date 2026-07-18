// Single source of truth for the notary/PPAT case workflow: the pipeline stages, the status enums
// used across Case/Bundle/OCR/Verification/QC/Approval, and their display metadata (label + theme
// color key + short code). StatusChip/ApprovalChip and every screen read from here so a status is
// never labeled or colored inconsistently between two screens.

// The end-to-end workflow, in order. Used by TimelineCard and progress indicators.
export const WORKFLOW_STAGES = [
  { key: 'CASE', label: 'Case' },
  { key: 'BUNDLE', label: 'Bundle Dokumen' },
  { key: 'UPLOAD', label: 'Upload' },
  { key: 'OCR_REVIEW', label: 'OCR Review' },
  { key: 'VERIFICATION', label: 'Verifikasi' },
  { key: 'DRAFT', label: 'Generate Draft' },
  { key: 'SEARCH', label: 'Search' },
  { key: 'REMINDER', label: 'Reminder' },
  { key: 'QC', label: 'QC' },
  { key: 'APPROVAL', label: 'Approval' },
  { key: 'LOCKED', label: 'Bundle Locked' },
  { key: 'DELIVERED', label: 'Delivered to Bank' },
];

// High-level case workflow for the WorkflowStepper. Data-driven so the stepper renders purely from
// these + a current index. Distinct from CASE_STATUS (which is the persisted status enum).
export const CASE_WORKFLOW = [
  { key: 'OPEN', label: 'Open' },
  { key: 'OCR', label: 'OCR' },
  { key: 'REVIEW', label: 'Review' },
  { key: 'GENERATE', label: 'Generate' },
  { key: 'QC', label: 'QC' },
  { key: 'READY', label: 'Ready' },
  { key: 'DELIVERED', label: 'Delivered' },
];

// Maps a persisted CASE_STATUS to the furthest reached CASE_WORKFLOW index (for the stepper).
//
// CASE_CREATED and UPLOADING both sit at OPEN (0): the case exists and its documents are still being
// collected, which is one stage to a reader even though they are two states to the aggregate.
// OCR_RUNNING is the first to move the stepper — it IS the OCR stage (1), so the stepper finally
// advances on real data instead of pinning every live case to stage 0.
//
// All three resolved to 0 through the `?? 0` fallback before they were named here; spelling them out
// means the fallback now only catches genuinely unknown statuses, which is what a fallback is for.
export const caseStatusToStage = (status) => (
  { CASE_CREATED: 0, UPLOADING: 0, OCR_RUNNING: 1, DRAFT: 0, WAITING_VERIFICATION: 2, WAITING_QC: 4, WAITING_APPROVAL: 4, READY_TO_SEND: 5, DELIVERED: 6, LOCKED: 6 }[status] ?? 0
);

// Bundle ingestion pipeline for the PipelineProgress widget.
export const BUNDLE_PIPELINE = [
  { key: 'OCR', label: 'OCR' },
  { key: 'NER', label: 'NER' },
  { key: 'CHUNK', label: 'Chunk' },
  { key: 'EMBEDDING', label: 'Embedding' },
  { key: 'INDEX', label: 'Index' },
  { key: 'COMPLETED', label: 'Completed' },
];

// Priority levels (PriorityChip). Colors are dedicated palette keys so priority never reuses status hues.
export const PRIORITY = {
  HIGH: { label: 'Tinggi', color: 'priorityHigh', icon: '🔴' },
  MEDIUM: { label: 'Sedang', color: 'priorityMedium', icon: '🟠' },
  LOW: { label: 'Rendah', color: 'priorityLow', icon: '⚪' },
};
export const priorityMeta = (p) => PRIORITY[p] || PRIORITY.MEDIUM;

// Case-level status. `color` is a theme color KEY (resolved through useTheme().colors[color]).
//
// Order matters twice: CaseListScreen builds its filter chips by walking this object, and the walk
// reads as a lifecycle. The first three are the EARLY states, added in Sprint 7 once caseEndpoint went
// live against the real backend. They are the whole reachable lifecycle without the ingestion
// pipeline: a case opens in CASE_CREATED, a human walks it to UPLOADING then OCR_RUNNING, and
// everything past that needs Role.SYSTEM. With no entry here the projection returned null and
// caseStatusMeta fell through to its unknown branch, so every row a notary could actually see wore a
// "Tidak diketahui" chip. A worklist that calls all of its rows unknown cannot be triaged.
//
// `info` twice is deliberate: UPLOADING and OCR_RUNNING are both "in progress, nothing for the human
// to do". Colour tracks what the reader must DO, not which state it is.
export const CASE_STATUS = {
  CASE_CREATED: { label: 'Baru Dibuka', color: 'textMuted', code: 'NEW' },
  UPLOADING: { label: 'Sedang Diunggah', color: 'info', code: 'UPLD' },
  OCR_RUNNING: { label: 'Sedang OCR', color: 'info', code: 'OCR' },
  DRAFT: { label: 'Draft', color: 'textFaint', code: 'DRAFT' },
  WAITING_VERIFICATION: { label: 'Menunggu Verifikasi', color: 'warning', code: 'VERIF' },
  WAITING_QC: { label: 'Menunggu QC', color: 'info', code: 'QC' },
  WAITING_APPROVAL: { label: 'Menunggu Approval', color: 'primary', code: 'APPR' },
  READY_TO_SEND: { label: 'Siap Kirim', color: 'success', code: 'READY' },
  DELIVERED: { label: 'Terkirim ke Bank', color: 'success', code: 'SENT' },
  LOCKED: { label: 'Terkunci', color: 'textMuted', code: 'LOCK' },
};

// Bundle sub-process status (OCR / verification / draft / QC / approval columns on a bundle).
export const STEP_STATUS = {
  PENDING: { label: 'Menunggu', color: 'textFaint', code: '—' },
  IN_PROGRESS: { label: 'Berjalan', color: 'warning', code: '…' },
  NEEDS_REVIEW: { label: 'Perlu Cek', color: 'warning', code: '!' },
  DONE: { label: 'Selesai', color: 'success', code: '✓' },
  REJECTED: { label: 'Ditolak', color: 'danger', code: '✕' },
};

// Field-level review decision in OCR Review / Verification.
export const FIELD_STATUS = {
  PENDING: { label: 'Belum', color: 'textFaint' },
  APPROVED: { label: 'Disetujui', color: 'success' },
  REJECTED: { label: 'Ditolak', color: 'danger' },
  NEEDS_CHECK: { label: 'Cek Manual', color: 'warning' },
};

// Approval outcome shown by ApprovalChip.
export const APPROVAL_STATUS = {
  PENDING: { label: 'Menunggu', color: 'textFaint' },
  APPROVED: { label: 'Disetujui', color: 'success' },
  REJECTED: { label: 'Ditolak', color: 'danger' },
};

// Reminder categories from the workflow (SKMHT/APHT deadlines, expiring identity docs, queue items).
export const REMINDER_TYPE = {
  SKMHT: { label: 'SKMHT', icon: '📜', color: 'danger' },
  APHT: { label: 'APHT', icon: '🏛️', color: 'warning' },
  EXPIRED_NIB: { label: 'NIB Kedaluwarsa', icon: '🏢', color: 'warning' },
  EXPIRED_NPWP: { label: 'NPWP Kedaluwarsa', icon: '🧾', color: 'warning' },
  NEED_VERIFICATION: { label: 'Perlu Verifikasi', icon: '🔍', color: 'info' },
  NEED_QC: { label: 'Perlu QC', icon: '✅', color: 'info' },
  NEED_APPROVAL: { label: 'Perlu Approval', icon: '✍️', color: 'primary' },
};

// Reminder urgency, derived from due date.
export const REMINDER_SEVERITY = {
  overdue: { label: 'Terlambat', color: 'danger' },
  soon: { label: 'Segera', color: 'warning' },
  normal: { label: 'Terjadwal', color: 'textMuted' },
};

// Safe lookups so an unknown status never crashes a screen.
export const caseStatusMeta = (s) => CASE_STATUS[s] || { label: s || 'Tidak diketahui', color: 'textMuted', code: '?' };
export const stepStatusMeta = (s) => STEP_STATUS[s] || { label: s || '—', color: 'textMuted', code: '?' };
export const fieldStatusMeta = (s) => FIELD_STATUS[s] || { label: s || 'Belum', color: 'textMuted' };
export const approvalStatusMeta = (s) => APPROVAL_STATUS[s] || { label: s || 'Menunggu', color: 'textMuted' };
export const reminderTypeMeta = (t) => REMINDER_TYPE[t] || { label: t || 'Pengingat', icon: '🔔', color: 'info' };
export const reminderSeverityMeta = (s) => REMINDER_SEVERITY[s] || REMINDER_SEVERITY.normal;
