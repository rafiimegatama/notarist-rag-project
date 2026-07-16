// BundleService — bundles for a case. Delegates to api/bundles (mock|http behind FEATURES.bundleEndpoint).
import { FEATURES } from '../constants/config';
import { listBundles, getBundle, getBundleDocuments } from '../api/bundles';

export const BundleService = {
  usingMock: !FEATURES.bundleEndpoint,
  listBundles: (caseId) => listBundles(caseId),
  getBundle: (bundleId) => getBundle(bundleId),
  getDocuments: (bundleId) => getBundleDocuments(bundleId),
};
