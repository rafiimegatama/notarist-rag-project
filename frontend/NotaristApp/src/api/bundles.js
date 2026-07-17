// bundleApi — bundles belonging to a case, plus the Human Verification sub-flow (a bundle-scoped
// concern, folded in here to keep the API surface at the 7 modules Sprint 2 specifies).
// No backend yet: FEATURES.bundleEndpoint / FEATURES.verificationEndpoint gate the real calls.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock } from './_support';
import { normalizeBundle } from '../models/Bundle';
import { MOCK_BUNDLES, MOCK_DOCUMENTS } from '../mocks/fixtures';

export async function listBundles(caseId) {
  if (FEATURES.bundleEndpoint) {
    const response = await client.get(`/cases/${caseId}/bundles`);
    return (response.data.data?.items ?? []).map(normalizeBundle);
  }
  return mock((MOCK_BUNDLES[caseId] ?? []).map(normalizeBundle), { label: 'bundles' });
}

export async function getBundle(bundleId) {
  if (FEATURES.bundleEndpoint) {
    const response = await client.get(`/bundles/${bundleId}`);
    return normalizeBundle(response.data.data);
  }
  const all = Object.values(MOCK_BUNDLES).flat();
  const found = all.find((b) => b.id === bundleId);
  return mock(found ? normalizeBundle(found) : normalizeBundle({ id: bundleId }), { label: 'bundle' });
}

export async function getBundleDocuments(bundleId) {
  if (FEATURES.bundleEndpoint) {
    const response = await client.get(`/bundles/${bundleId}/documents`);
    return response.data.data?.items ?? [];
  }
  return mock(MOCK_DOCUMENTS[bundleId] ?? [], { label: 'bundle-docs' });
}

// --- Human Verification (bundle-scoped) ---
// POST /bundles/{id}/verification { decisions: [{fieldId, decision, comment}] }
export async function submitVerification(bundleId, decisions) {
  if (FEATURES.verificationEndpoint) {
    const response = await client.post(`/bundles/${bundleId}/verification`, { decisions });
    return response.data.data;
  }
  return mock({ bundleId, accepted: decisions.length, at: new Date().toISOString() }, { label: 'verification-submit', delay: 500 });
}
