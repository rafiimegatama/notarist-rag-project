// CaseService — the seam the case screens/state depend on. Delegates to api/cases (mock|http behind
// FEATURES.caseEndpoint). See services/contracts.js#CaseServiceContract.
import { FEATURES } from '../constants/config';
import { listCases, getCase } from '../api/cases';

export const CaseService = {
  usingMock: !FEATURES.caseEndpoint,
  listCases: (query) => listCases(query),
  getCase: (id) => getCase(id),
};
