// caseApi — Case is the workflow root entity. No backend endpoint exists yet (FEATURES.caseEndpoint),
// so each call falls back to marked mock fixtures. The real HTTP shape is written out and commented
// so wiring it up later is a one-line flag flip + uncomment.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock } from './_support';
import { unwrap, toList } from './envelope';
import { normalizeCase } from '../models/Case';
import { toTimelineEntries } from '../models/Timeline';
import { normalizePage } from './pagination';
import { MOCK_CASES, MOCK_TIMELINE } from '../mocks/fixtures';

// GET /cases?page&size&q&status  -> paginated cases
export async function listCases({ page = 0, size = 20, query = '', status = null } = {}) {
  if (FEATURES.caseEndpoint) {
    const response = await client.get('/cases', { params: { page, size, q: query || undefined, status: status || undefined } });
    // Sprint 5 (Task 8): was `body.data?.items ?? []` + `body.data?.page ?? {}`. toList handles both
    // `{items}` and a Spring `{content}`; normalizePage handles a nested descriptor OR top-level
    // Spring fields. The old code turned the Spring layout into `page: {}`, which made hasMore false
    // and silently truncated the list at page one.
    const payload = unwrap(response, null);
    return {
      items: toList(payload, ['items', 'content']).map(normalizeCase),
      page: normalizePage(payload, { page, size }),
      __mock: false,
    };
  }
  // Mock path: filter + paginate the fixtures client-side.
  let items = MOCK_CASES.map(normalizeCase);
  if (query) {
    const q = query.toLowerCase();
    items = items.filter((c) => c.debtorName.toLowerCase().includes(q) || c.caseNumber.toLowerCase().includes(q) || c.bank.toLowerCase().includes(q));
  }
  if (status) items = items.filter((c) => c.status === status);
  const total = items.length;
  const start = page * size;
  const paged = items.slice(start, start + size);
  return mock({ items: paged, page: { number: page, size, totalElements: total, totalPages: Math.max(1, Math.ceil(total / size)) } }, { label: 'cases' });
}

export async function getCase(caseId) {
  if (FEATURES.caseEndpoint) {
    const response = await client.get(`/cases/${caseId}`);
    // unwrap, not `response.data.data`: the latter throws a TypeError when a proxy or gateway
    // returns a body that is not the envelope (Sprint 5, Task 1).
    return normalizeCase(unwrap(response, null));
  }
  const found = MOCK_CASES.find((c) => c.id === caseId);
  return mock(found ? normalizeCase(found) : normalizeCase({ id: caseId }), { label: 'case' });
}

export async function getCaseTimeline(caseId) {
  if (FEATURES.caseEndpoint) {
    const response = await client.get(`/cases/${caseId}/timeline`);
    // Sprint 5 (Task 1/2) — this returned `response.data.data ?? []`, the raw payload, and that was
    // a live bug: TimelineResponse is an OBJECT ({ timelineId, caseId, entries[], … }), not an
    // array. The screens map over the result, so against the real endpoint this produced
    // "entries.map is not a function" or a silently empty timeline. toTimelineEntries accepts the
    // object, a bare array (fixtures) or a paged wrapper, and always yields a sorted entry list.
    return Object.assign(toTimelineEntries(unwrap(response, null)), { __mock: false });
  }
  return mock(toTimelineEntries(MOCK_TIMELINE[caseId] ?? []), { label: 'timeline' });
}
