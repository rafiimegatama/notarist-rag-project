// Search result normalization (Sprint 5, Task 9).
//
// The brief asks for six response variants to work with NO UI rewrite:
//
//   structured | semantic | hybrid | citation | empty | partial
//
// The SearchScreen renders exactly three things off a result: a grounding chip, a citation count +
// timing line, and a list of <CitationCard>. So "support every variant" reduces to one requirement:
// whatever the backend sends, produce that same shape — with `citations` always an array, and
// anything unknown left null rather than faked. Every variant below then falls out of one code path:
//
//   structured  -> citations keyed by document/chunk; intent is a literal LOOKUP
//   semantic    -> same shape, backend-classified intent, grounding present
//   hybrid      -> both retrieval modes merged server-side; may carry `retrievalMode`/scores
//   citation    -> a citation-only answer (no synthesized text) — citations present, answer null
//   empty       -> citations: [] with a real grounding/timing — a valid answer meaning "nothing"
//   partial     -> some fields absent (e.g. grounding still computing, or a degraded retriever)
//
// The screen already distinguishes empty from failed (`!citations.length` -> EmptyState), so the one
// thing this must never do is turn an empty result into a null result, or a partial one into a throw.
import { pick, str, num, list, obj, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

export const GROUNDING_LEVELS = ['HIGH', 'MEDIUM', 'LOW'];

const CITATION_CONSUMED = [
  'chunkId', 'id', 'documentId', 'documentTitle', 'title', 'snippet', 'text', 'content',
  'score', 'relevanceScore', 'page', 'pageNumber', 'documentType', 'classificationLevel', 'sourceUri',
];

export function normalizeCitation(raw = {}) {
  const c = obj(raw, null);
  if (!c) return null;
  const rawId = pick(c, ['chunkId', 'id']);
  const out = {
    chunkId: rawId === null ? null : String(rawId),
    documentId: str(pick(c, ['documentId']), null),
    // CitationCard shows a title line; `documentTitle` is the likely field, `title` the fallback.
    documentTitle: str(pick(c, ['documentTitle', 'title']), null),
    // The cited text itself. Three plausible spellings — a citation with no text is useless, so it
    // is worth reading widely here rather than rendering a blank card.
    snippet: str(pick(c, ['snippet', 'text', 'content']), null),
    score: num(pick(c, ['score', 'relevanceScore']), null),
    page: num(pick(c, ['page', 'pageNumber']), null),
    documentType: str(pick(c, ['documentType']), null),
    classificationLevel: str(pick(c, ['classificationLevel']), null),
    sourceUri: str(pick(c, ['sourceUri']), null),
  };
  return withExtras(out, c, CITATION_CONSUMED);
}

export const CitationNormalizer = makeNormalizer(normalizeCitation);

const CONSUMED = [
  'queryId', 'intent', 'normalizedQuery', 'citations', 'results', 'hits', 'items',
  'groundingLevel', 'grounding', 'retrievedChunkCount', 'processingTimeMs', 'tookMs',
  'answer', 'answerText', 'retrievalMode', 'mode', 'degraded', 'partial',
];

/**
 * @param {any} raw          the unwrapped SearchResponse payload
 * @param {string} [requestedMode]  'semantic' | 'structured' — what the client asked for, used only
 *                                  as a last-resort label when the server does not echo one back.
 */
export function normalizeSearchResult(raw = {}, requestedMode = null) {
  const source = obj(raw, {}) || {};

  // `citations` is the documented field; the others cover a hybrid/structured retriever that names
  // its output differently. toList also unwraps a paged {items:[...]} without special-casing.
  const citations = CitationNormalizer.list(
    toList(source, ['citations', 'results', 'hits', 'items']),
  );

  const out = {
    queryId: str(pick(source, ['queryId']), null),
    intent: str(pick(source, ['intent']), null),
    normalizedQuery: str(pick(source, ['normalizedQuery']), null),

    // ALWAYS an array. This single guarantee is what makes the `empty` and `partial` variants
    // render instead of crashing: the screen does `result.citations ?? []` then `.length`, and a
    // null here would have been indistinguishable from "no result at all".
    citations,

    // null when the server did not compute one (the `partial` variant). The screen already renders
    // "Grounding: —" for null, so an absent grounding degrades to a dash rather than a false claim.
    groundingLevel: str(pick(source, ['groundingLevel', 'grounding']), null),

    // Server count preferred: it can exceed citations.length when the server truncates to maxResults,
    // and that difference is real information ("we found more than we show").
    retrievedChunkCount: num(pick(source, ['retrievedChunkCount']), null) ?? citations.length,
    processingTimeMs: num(pick(source, ['processingTimeMs', 'tookMs']), null),

    // The `citation` variant answers with sources only and no synthesized prose — answer stays null
    // and the screen simply renders the citation list, which is already what it does.
    answer: str(pick(source, ['answer', 'answerText']), null),

    // Which retrieval actually ran. Echoed by the server when it classifies (hybrid/semantic);
    // falls back to what we asked for, and never invents a third value.
    retrievalMode: str(pick(source, ['retrievalMode', 'mode']), null) ?? requestedMode,

    // A degraded/partial answer — e.g. the vector store timed out and only lexical hits came back.
    // Surfaced so a screen can caveat the result; absent means "not degraded", not "unknown".
    degraded: pick(source, ['degraded', 'partial']) === true,
  };
  return withExtras(out, source, CONSUMED);
}

export const SearchNormalizer = makeNormalizer(normalizeSearchResult);
