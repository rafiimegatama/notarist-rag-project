// caseApi — Case is the workflow root entity. No backend endpoint exists yet (FEATURES.caseEndpoint),
// so each call falls back to marked mock fixtures. The real HTTP shape is written out and commented
// so wiring it up later is a one-line flag flip + uncomment.
import client from './client';
import { FEATURES } from '../constants/config';
import { mock } from './_support';
import { unwrap, toList } from './envelope';
import { normalizeCase, toCaseStateFilter } from '../models/Case';
import { toTimelineEntries } from '../models/Timeline';
import { normalizePage } from './pagination';
import { MOCK_CASES, MOCK_TIMELINE } from '../mocks/fixtures';

// GET /cases?status&caseType&assignedNotarisId&assignedStaff&createdFrom&createdTo&page&size
//   -> ApiResponse<PageResponse<CaseResponse>> = { items: CaseResponse[], page: PageInfo }
//
// That param list is the WHOLE contract (CaseController.listCases). Two things it notably does NOT
// have, both of which this module used to send anyway:
//
//   q       — no free-text param exists. Spring silently DROPS an unknown query param, so `?q=budi`
//             returned the UNFILTERED page 1 dressed up as a search result. The user typed a debtor's
//             name, got 20 unrelated cases, and nothing anywhere said the search had not run. That is
//             the worst failure mode in this file: not an error, a lie. It is now surfaced (see
//             `unsupportedFilters`) instead of being sent into the void.
//
//   status  — exists, but it is `CaseState.valueOf(status)` on the raw string: a value outside the
//             enum is a 400, not an empty list. This sent the UI's own 7-value vocabulary, so DRAFT,
//             WAITING_APPROVAL, READY_TO_SEND and LOCKED — 4 of the 7 filter chips — were guaranteed
//             400s. toCaseStateFilter projects the chip onto the real CaseState.
export async function listCases({ page = 0, size = 20, query = '', status = null } = {}) {
  if (FEATURES.caseEndpoint) {
    const q = (query || '').trim();
    const state = toCaseStateFilter(status);

    const params = { page, size };
    // Only send `status` when the chip maps to a real CaseState. An unmappable chip means the filter
    // could not be applied — say so via unsupportedFilters rather than sending a literal that 400s or
    // (worse) omitting it silently and returning the unfiltered list as though it were filtered.
    if (state) params.status = state;

    const response = await client.get('/cases', { params });
    // Sprint 5 (Task 8): was `body.data?.items ?? []` + `body.data?.page ?? {}`. toList handles both
    // `{items}` and a Spring `{content}`; normalizePage handles a nested descriptor OR top-level
    // Spring fields. The old code turned the Spring layout into `page: {}`, which made hasMore false
    // and silently truncated the list at page one.
    const payload = unwrap(response, null);

    // Filters the caller asked for that the backend cannot honour. The screen renders this as a
    // banner; the alternative — returning these rows as though the filter had applied — is the
    // fabrication this sprint exists to remove. NOT client-side filtered as a consolation: this is
    // page 1 of N, so filtering it here would search only the 20 rows we happen to hold and report
    // "no results" for a case sitting on page 2.
    const unsupportedFilters = [];
    if (q) unsupportedFilters.push('query');
    if (status && !state) unsupportedFilters.push('status');

    return {
      items: toList(payload, ['items', 'content']).map(normalizeCase),
      page: normalizePage(payload, { page, size }),
      unsupportedFilters,
      __mock: false,
    };
  }
  // Mock path: filter + paginate the fixtures client-side. Honest here in a way it cannot be against
  // the real endpoint — the fixtures ARE the whole dataset, so filtering them locally really does
  // filter everything. `unsupportedFilters` is therefore empty, and stays in the shape so callers
  // read one contract on both paths.
  let items = MOCK_CASES.map(normalizeCase);
  if (query) {
    const q = query.toLowerCase();
    // Null-safe across every searchable field: normalizeCase returns null (not a placeholder) for
    // anything the source omits, so a bare .toLowerCase() here is a TypeError waiting for the first
    // fixture that lacks a debtor. Match the same fields the row renders.
    const hit = (v) => typeof v === 'string' && v.toLowerCase().includes(q);
    items = items.filter(
      (c) => hit(c.caseNumber) || hit(c.debtorName) || hit(c.bank) || hit(c.caseType) || hit(c.nomorAkta),
    );
  }
  if (status) items = items.filter((c) => c.status === status);
  const total = items.length;
  const start = page * size;
  const paged = items.slice(start, start + size);
  return mock({
    items: paged,
    page: { number: page, size, totalElements: total, totalPages: Math.max(1, Math.ceil(total / size)) },
    unsupportedFilters: [],
  }, { label: 'cases' });
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
