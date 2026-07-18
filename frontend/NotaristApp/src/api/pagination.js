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
//   Notarist  { items: [...], page: { number, size, totalElements, totalPages,
//                                     hasNext, hasPrevious, isFirst, isLast } }
//   Spring    { content: [...], number, size, totalElements, totalPages, first, last }
//
// and produces one page descriptor. Where the server says nothing, the fields are null rather than
// invented — `hasMore` then falls back to a fact we can actually observe (did we get a full page?)
// instead of a guess dressed as a total.
//
// Sprint 6: the nested layout above is now read from the REAL DTO
// (backend/notarist-core/.../api/response/PageInfo.java) rather than assumed. Two corrections fell
// out of that, both silent:
//
//   `last`     — PageInfo's component is `isLast`, and Jackson serializes a record component under
//                its own name, so the JSON key is "isLast". Nothing ever sent "last", so this field
//                was unconditionally null and rung 1 of hasMorePages was dead code. It happened to
//                degrade to `totalPages` arithmetic, which is right — so this was a latent trap, not
//                a live bug: the moment a backend sent items without a total, pagination would have
//                stopped at page one with no clue why.
//
//   `hasNext`  — PageInfo answers "is there more" DIRECTLY and we were ignoring it to do arithmetic.
//                It is now the top rung.
import { pick, num, count, obj, triBool } from '../models/normalize';

/**
 * @returns {{number:number, size:number|null, totalElements:number|null, totalPages:number|null,
 *            hasNext:boolean|null, last:boolean|null}}
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
    // PageInfo.hasNext — the server's own, direct answer.
    hasNext: triBool(pick(p, ['hasNext'])),
    // PageInfo sends `isLast`; a stock Spring Page sends `last`. Accept both spellings.
    last: triBool(pick(p, ['isLast', 'last'])),
  };
}

/**
 * Is there another page? Ordered by how much the server actually told us:
 *   1. `hasNext`     — the server answering this exact question
 *   2. `last`        — the server's verdict, inverted
 *   3. `totalPages`  — arithmetic on a real total
 *   4. observation   — a full page came back, so there is plausibly another
 *
 * Rung 4 is the important one. Previously a missing `totalPages` defaulted to 1 and pagination died
 * silently. Guessing "maybe more" costs one extra request that returns an empty page and stops;
 * guessing "no more" costs the user their data with no way to tell.
 */
export function hasMorePages(page, receivedCount) {
  if (!page) return false;
  if (page.hasNext !== null && page.hasNext !== undefined) return page.hasNext;
  if (page.last !== null && page.last !== undefined) return !page.last;
  if (page.totalPages !== null) return page.number < page.totalPages - 1;
  if (page.size !== null && receivedCount != null) return receivedCount >= page.size;
  return false;
}
