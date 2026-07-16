// VerificationService — human verification / QC checklist for a bundle. The checklist is derived from
// the bundle's documents; submission delegates to api/bundles#submitVerification (mock|http behind
// FEATURES.verificationEndpoint).
import { FEATURES } from '../constants/config';
import { getBundleDocuments, submitVerification } from '../api/bundles';

export const VerificationService = {
  usingMock: !FEATURES.verificationEndpoint,
  getChecklist: (bundleId) => getBundleDocuments(bundleId),
  submit: (bundleId, decisions) => submitVerification(bundleId, decisions),
};
