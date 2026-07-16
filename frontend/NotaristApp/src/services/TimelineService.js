// TimelineService — case workflow timeline. Delegates to api/cases#getCaseTimeline (mock|http behind
// FEATURES.caseEndpoint).
//
// GET /api/v1/cases/{caseId}/timeline exists and works. This still serves mocks, and deliberately:
// a timeline is addressed BY CASE ID. While caseEndpoint is false the case list is fixtures, so the
// only IDs the UI can hand this service are fixture IDs ('case-001'), which no backend has ever
// heard of — every real call would 404. Timeline cannot go live before the case list does; the two
// are coupled through the ID, not through a flag.
//
// It therefore stays on caseEndpoint on purpose. Giving it its own flag would let someone turn it on
// alone and get a screen of 404s. See constants/config.js for why caseEndpoint is false.
import { FEATURES } from '../constants/config';
import { getCaseTimeline } from '../api/cases';

export const TimelineService = {
  usingMock: !FEATURES.caseEndpoint,
  getCaseTimeline: (caseId) => getCaseTimeline(caseId),
};
