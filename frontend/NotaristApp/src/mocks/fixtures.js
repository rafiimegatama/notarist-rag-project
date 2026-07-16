// Mock fixtures for the case-workflow screens whose backend does not exist yet (Sprint 2).
// Everything here is clearly fake sample data — the API layer only reaches for it when the matching
// FEATURES flag is false, and every screen that renders it shows a "data contoh" banner. When the
// real endpoints ship, flip the flags in constants/config.js and these are never touched again.
//
// Relationships are internally consistent: bundles reference caseId, reminders reference caseNumber,
// so navigating case -> bundle -> reminder shows coherent data during UX review.

const now = Date.parse('2026-07-14T09:00:00+07:00');
const days = (n) => new Date(now + n * 86400000).toISOString();

export const MOCK_CASES = [
  {
    id: 'case-001', caseNumber: 'KPR-2026-0142', debtorName: 'Budi Santoso',
    bank: 'Bank Mandiri', status: 'WAITING_VERIFICATION', bundleCount: 3,
    collateralType: 'SHM Rumah Tinggal', notaris: 'Notaris A. Wijaya, S.H., M.Kn.',
    createdAt: days(-6), updatedAt: days(-1),
  },
  {
    id: 'case-002', caseNumber: 'KPR-2026-0138', debtorName: 'Siti Rahmawati',
    bank: 'BCA', status: 'WAITING_QC', bundleCount: 2,
    collateralType: 'SHM Ruko', notaris: 'Notaris A. Wijaya, S.H., M.Kn.',
    createdAt: days(-9), updatedAt: days(-2),
  },
  {
    id: 'case-003', caseNumber: 'KPR-2026-0131', debtorName: 'Andi Pratama',
    bank: 'BNI', status: 'WAITING_APPROVAL', bundleCount: 4,
    collateralType: 'HGB Apartemen', notaris: 'Notaris R. Kusuma, S.H.',
    createdAt: days(-12), updatedAt: days(-3),
  },
  {
    id: 'case-004', caseNumber: 'KPR-2026-0125', debtorName: 'Dewi Lestari',
    bank: 'Bank Mandiri', status: 'READY_TO_SEND', bundleCount: 3,
    collateralType: 'SHM Tanah Kavling', notaris: 'Notaris A. Wijaya, S.H., M.Kn.',
    createdAt: days(-15), updatedAt: days(-1),
  },
  {
    id: 'case-005', caseNumber: 'KPR-2026-0119', debtorName: 'Rudi Hartono',
    bank: 'BRI', status: 'DRAFT', bundleCount: 1,
    collateralType: 'SHM Rumah Tinggal', notaris: 'Notaris R. Kusuma, S.H.',
    createdAt: days(-2), updatedAt: days(0),
  },
  {
    id: 'case-006', caseNumber: 'KPR-2026-0110', debtorName: 'Maya Anggraini',
    bank: 'BCA', status: 'DELIVERED', bundleCount: 3,
    collateralType: 'HGB Ruko', notaris: 'Notaris A. Wijaya, S.H., M.Kn.',
    createdAt: days(-24), updatedAt: days(-5),
  },
];

export const MOCK_TIMELINE = {
  'case-001': [
    { id: 't1', stage: 'CASE', label: 'Case dibuat', at: days(-6), actor: 'Admin Kantor', done: true },
    { id: 't2', stage: 'UPLOAD', label: '3 bundle diunggah', at: days(-5), actor: 'Staf Legal', done: true },
    { id: 't3', stage: 'OCR_REVIEW', label: 'OCR selesai (2/3 bundle)', at: days(-3), actor: 'Sistem OCR', done: true },
    { id: 't4', stage: 'VERIFICATION', label: 'Verifikasi berlangsung', at: days(-1), actor: 'Notaris A. Wijaya', done: false },
  ],
};

export const MOCK_BUNDLES = {
  'case-001': [
    {
      id: 'bnd-001', caseId: 'case-001', name: 'Bundle Identitas Debitur', documentCount: 4,
      ocrStatus: 'DONE', verificationStatus: 'IN_PROGRESS', draftStatus: 'PENDING',
      qcStatus: 'PENDING', approvalStatus: 'PENDING', updatedAt: days(-1),
    },
    {
      id: 'bnd-002', caseId: 'case-001', name: 'Bundle Sertifikat Jaminan', documentCount: 3,
      ocrStatus: 'DONE', verificationStatus: 'NEEDS_REVIEW', draftStatus: 'PENDING',
      qcStatus: 'PENDING', approvalStatus: 'PENDING', updatedAt: days(-2),
    },
    {
      id: 'bnd-003', caseId: 'case-001', name: 'Bundle Dokumen Bank', documentCount: 5,
      ocrStatus: 'IN_PROGRESS', verificationStatus: 'PENDING', draftStatus: 'PENDING',
      qcStatus: 'PENDING', approvalStatus: 'PENDING', updatedAt: days(0),
    },
  ],
};

export const MOCK_DOCUMENTS = {
  'bnd-001': [
    { id: 'doc-1', name: 'KTP Debitur.pdf', type: 'KTP', ocrStatus: 'DONE', pages: 1 },
    { id: 'doc-2', name: 'KTP Pasangan.pdf', type: 'KTP', ocrStatus: 'DONE', pages: 1 },
    { id: 'doc-3', name: 'Kartu Keluarga.pdf', type: 'KK', ocrStatus: 'DONE', pages: 2 },
    { id: 'doc-4', name: 'NPWP.pdf', type: 'NPWP', ocrStatus: 'NEEDS_REVIEW', pages: 1 },
  ],
};

// OCR-extracted fields for one document, with confidence + review state (OCR Review screen).
export const MOCK_OCR_FIELDS = {
  'doc-1': {
    documentId: 'doc-1', documentName: 'KTP Debitur.pdf', pageCount: 1,
    stampDetected: false, signatureDetected: true, overallConfidence: 0.91,
    fields: [
      { id: 'f1', label: 'NIK', value: '3174091205880003', confidence: 0.98, status: 'PENDING', bbox: { x: 0.32, y: 0.18, w: 0.4, h: 0.05 } },
      { id: 'f2', label: 'Nama', value: 'BUDI SANTOSO', confidence: 0.95, status: 'PENDING', bbox: { x: 0.32, y: 0.25, w: 0.5, h: 0.05 } },
      { id: 'f3', label: 'Tempat/Tgl Lahir', value: 'JAKARTA, 12-05-1988', confidence: 0.88, status: 'PENDING', bbox: { x: 0.32, y: 0.32, w: 0.5, h: 0.05 } },
      { id: 'f4', label: 'Alamat', value: 'JL. MELATI NO. 12 RT 003/004', confidence: 0.72, status: 'NEEDS_CHECK', bbox: { x: 0.32, y: 0.42, w: 0.55, h: 0.08 } },
      { id: 'f5', label: 'Pekerjaan', value: 'KARYAWAN SWASTA', confidence: 0.61, status: 'NEEDS_CHECK', bbox: { x: 0.32, y: 0.55, w: 0.45, h: 0.05 } },
    ],
    // "Timeline Direksi" — the board/authority approval trail for high-value collateral.
    authorityTimeline: [
      { id: 'a1', role: 'Notaris', name: 'A. Wijaya, S.H., M.Kn.', decision: 'PENDING', at: null },
      { id: 'a2', role: 'Direksi Bank', name: 'Kepala Cabang Mandiri', decision: 'PENDING', at: null },
    ],
  },
};

export const MOCK_REMINDERS = [
  { id: 'r1', type: 'SKMHT', title: 'SKMHT jatuh tempo — wajib tingkatkan ke APHT', caseNumber: 'KPR-2026-0142', dueDate: days(-2) },
  { id: 'r2', type: 'APHT', title: 'Pendaftaran APHT ke BPN', caseNumber: 'KPR-2026-0131', dueDate: days(3) },
  { id: 'r3', type: 'EXPIRED_NPWP', title: 'NPWP debitur kedaluwarsa', caseNumber: 'KPR-2026-0138', dueDate: days(5) },
  { id: 'r4', type: 'EXPIRED_NIB', title: 'NIB perusahaan penjamin kedaluwarsa', caseNumber: 'KPR-2026-0131', dueDate: days(12) },
  { id: 'r5', type: 'NEED_VERIFICATION', title: 'Bundle Sertifikat perlu verifikasi manual', caseNumber: 'KPR-2026-0142', dueDate: days(1) },
  { id: 'r6', type: 'NEED_QC', title: 'QC checklist belum dijalankan', caseNumber: 'KPR-2026-0138', dueDate: days(2) },
  { id: 'r7', type: 'NEED_APPROVAL', title: 'Menunggu approval notaris', caseNumber: 'KPR-2026-0131', dueDate: days(4) },
];

export const MOCK_CONVERSATIONS = [
  { id: 'cv1', sessionId: 'sess-a1', title: 'Syarat SKMHT untuk KPR', lastMessage: 'SKMHT berlaku 1 bulan untuk tanah terdaftar…', updatedAt: days(0) },
  { id: 'cv2', sessionId: 'sess-a2', title: 'Perbedaan HGB dan SHM', lastMessage: 'HGB memiliki jangka waktu, SHM tidak…', updatedAt: days(0) },
  { id: 'cv3', sessionId: 'sess-a3', title: 'Bea perolehan hak (BPHTB)', lastMessage: 'BPHTB dihitung 5% dari NPOP…', updatedAt: days(-1) },
  { id: 'cv4', sessionId: 'sess-a4', title: 'Roya hak tanggungan', lastMessage: 'Roya dilakukan setelah pelunasan…', updatedAt: days(-4) },
  { id: 'cv5', sessionId: 'sess-a5', title: 'Syarat balik nama sertifikat', lastMessage: 'Dibutuhkan AJB, bukti bayar pajak…', updatedAt: days(-20) },
];

export function computeDashboardSummary(cases = MOCK_CASES, reminders = MOCK_REMINDERS) {
  const by = (s) => cases.filter((c) => c.status === s).length;
  return {
    totalCase: cases.length,
    draft: by('DRAFT'),
    waitingVerification: by('WAITING_VERIFICATION'),
    waitingQc: by('WAITING_QC'),
    waitingApproval: by('WAITING_APPROVAL'),
    readyToSend: by('READY_TO_SEND'),
    overdueSkmht: reminders.filter((r) => r.type === 'SKMHT' && Date.parse(r.dueDate) < now).length,
    reminderCount: reminders.length,
  };
}

export const MOCK_NOW = now;
