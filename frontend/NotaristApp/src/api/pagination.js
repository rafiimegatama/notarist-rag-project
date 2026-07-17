// Pagination shape handling (Sprint 5, Task 8).
//
// api/cases.js read `body.data?.page ?? {}` and CaseContext then did `data.page?.totalPages ?? 1`.
// That chain fails SILENTLY, which is what makes it worth its own module: if the backend answers a
// standard Spring `Page` (which puts totalPages/number/size at the TOP level, alongside `content`,
// with no nested `page` object), then `page` is `{}`, `totalPages` defaults to 1, `hasMore` computes
// false — and the list simply stops after page one. No error, no empty state, no clue. The user just
// never sees case 21.
//
// So this reads BOTH layouts:
//
//   nested   { items: [...], page: { number, size, totalElements, totalPages } }
//   Spring   { content: [...], number, size, totalElements, totalPages, first, last }
//
// and produces one page descriptor. Where the server says nothing, the fields are null rather than
// invented — `hasMore` then falls back to a fact we can actually observe (did we get a full page?)
// instead of a guess dressed as a total.
import { pick, num, count, obj } from '../models/normalize';

/**
 * @returns {{number:number, size:number|null, totalElements:number|null, totalPages:number|null,
 *            last:boolean|null}}
 */
export function normalizePage(payload, requested = {}) {
  const source = obj(payload, {}) || {};
  // Nested descriptor if present, else the payload itself (Spring puts them at the top level).
  const p = obj(pick(source, ['page']), null) || source;

  return {
    // The page we asked for is the reliable anchor: `number` is absent in some shapes, and a wrong
    // page index corrupts the next fetch.
    number: num(pick(p, ['number', 'pageNumber']), null) ?? count(requested.page, 0),
    size: num(pick(p, ['size', 'pageSize']), null) ?? (requested.size != null ? count(requested.size, null) : null),
    totalElements: num(pick(p, ['totalElements', 'totalCount', 'total']), null),
    totalPages: num(pick(p, ['totalPages']), null),
    // Spring sends `last`. It is the most direct answer to "is there more", when present.
    last: pick(p, ['last']) === true ? true : (pick(p, ['last']) === false ? false : null),
  };
}

/**
 * Is there another page? Ordered by how much the server actually told us:
 *   1. `last`        — the server's own verdict
 *   2. `totalPages`  — arithmetic on a real total
 *   3. observation   — a full page came back, so there is plausibly another
 *
 * Rung 3 is the important one. Previously a missing `totalPages` defaulted to 1 and pagination died
 * silently. Guessing "maybe more" costs one extra request that returns an empty page and stops;
 * guessing "no more" costs the user their data with no way to tell.
 */
export function hasMorePages(page, receivedCount) {
  if (!page) return false;
  if (page.last !== null) return !page.last;
  if (page.totalPages !== null) return page.number < page.totalPages - 1;
  if (page.size !== null && receivedCount != null) return receivedCount >= page.size;
  return false;
}
