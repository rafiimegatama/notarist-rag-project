// VerificationService — human verification / QC checklist for a bundle.
//
// Sprint 6: `getChecklist` used to call api/bundles#getBundleDocuments() and hand the screen a
// DOCUMENT list, which the screen then treated as a checklist (one row per document). That was
// reasonable when it was written — there was no verification backend — and is now simply wrong:
// VerificationController serves a real VerificationResponse whose `checklist[]` is a list of legal
// CHECKS, each with its own id, category, mandatory flag and decision.
//
// The distinction is not cosmetic. Decisions are recorded per checklist ITEM id
// (POST /bundles/{id}/verification/checklist/{itemId}), so a screen holding document ids had nothing
// it could legally submit — every id it owned was for the wrong entity. It also could not represent a
// mandatory check that spans documents ("sertifikat matches the deed"), which is most of what human
// verification is for.
// Sprint 7: `usingMock` removed for the same reason as OCRService's. api/bundles#getVerification no
// longer has a mock path — it handed an EMPTY checklist to every real bundle uuid, which a notary
// reads as "nothing to verify" — so a flag advertising mock data now describes something that cannot
// happen.
import { getVerification, submitVerification, submitChecklistItem } from '../api/bundles';

export const VerificationService = {
  /** -> normalized VerificationResponse { checklist[], progress, summary, … } */
  getChecklist: (bundleId) => getVerification(bundleId),
  /** decisions: [{ itemId, decision, comment }] — fanned out to one POST per item by the api layer. */
  submit: (bundleId, decisions) => submitVerification(bundleId, decisions),
  /** Record a single decision without submitting the whole checklist. */
  submitItem: (bundleId, itemId, payload) => submitChecklistItem(bundleId, itemId, payload),
};
