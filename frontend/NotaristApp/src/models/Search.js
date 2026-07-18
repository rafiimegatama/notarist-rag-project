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
import { pick, str, num, obj, triBool, withExtras, makeNormalizer } from './normalize';
import { toList } from '../api/envelope';

// Mirror of backend GroundingScore.Level
// (backend/notarist-search/.../domain/model/GroundingScore.java):
//
//   HIGH (>= 0.75) | MEDIUM (>= 0.50) | LOW (>= 0.25) | UNGROUNDED (< 0.25)
//
// UNGROUNDED was missing until Sprint 6. It is not an edge case — GroundingScore.ungrounded() is a
// named factory the backend returns whenever retrieval finds nothing usable, so it is the level a
// FAILED search reports. Omitting it meant oneOf() mapped the single most important level to null,
// and the screen rendered "Grounding: —" — "we did not measure this" — for a result the backend had
// explicitly measured and rejected. A dash where the answer is "do not trust this" is the worst
// possible lie for a legal search tool.
export const GROUNDING_LEVELS = ['HIGH', 'MEDIUM', 'LOW', 'UNGROUNDED'];

// Mirror of backend SearchIntent (backend/notarist-search/.../domain/model/SearchIntent.java).
// SearchRequest.intentOverride is deserialized straight into this enum, so anything not on this list
// is an HTTP 400, not a fallback.
export const SEARCH_INTENTS = [
  'DOCUMENT_LOOKUP',
  'REGULATION_LOOKUP',
  'SEMANTIC_QUESTION',
  'RELATED_DOCUMENT',
  'CITATION_LOOKUP',
];

export const SEARCH_INTENT = {
  DOCUMENT_LOOKUP: 'DOCUMENT_LOOKUP',
  REGULATION_LOOKUP: 'REGULATION_LOOKUP',
  SEMANTIC_QUESTION: 'SEMANTIC_QUESTION',
  RELATED_DOCUMENT: 'RELATED_DOCUMENT',
  CITATION_LOOKUP: 'CITATION_LOOKUP',
};

const CITATION_CONSUMED = [
  'chunkId', 'id', 'documentId', 'sourceType', 'retrievalReason', 'relevanceScore', 'score',
  'citationText', 'snippet', 'text', 'content', 'sourceObjectKey', 'sourceUri', 'chunkIndex',
  'documentTitle', 'title', 'page', 'pageNumber', 'documentType', 'classificationLevel',
];

/**
 * One citation (CitationResponse).
 *
 * Sprint 6 rewrote this against the real DTO. It was the sprint's worst LIVE bug, and it was live on
 * the one endpoint that is actually switched on (searchEndpoint: true) — so this ran on every real
 * search anyone has ever performed. CitationResponse sends:
 *
 *   chunkId, documentId, sourceType, retrievalReason, relevanceScore, citationText,
 *   sourceObjectKey, chunkIndex
 *
 * and this normalizer emitted `score`, `snippet`, `documentTitle`, `page`, `documentType`,
 * `classificationLevel`, `sourceUri` — a vocabulary it had invented. CitationCard's own docblock says
 * it renders `{ sourceType, relevanceScore, citationText, retrievalReason }` — the DTO's names,
 * correctly — so the normalizer sat between two ends that agreed with each other and translated one
 * into a language neither spoke. Every citation on a legal search rendered:
 *
 *   "[1] undefined"        sourceType was dropped entirely
 *   "skor 0.00"            relevanceScore -> undefined -> CitationCard's `?? 0` -> a FABRICATED score
 *   (blank body)           citationText -> undefined -> the cited text itself, missing
 *
 * "skor 0.00" is the part that matters: not a blank, a NUMBER. The app told notaries every source
 * backing a legal answer had zero relevance, in a chip styled to look like a real measurement.
 *
 * The DTO's names are now the output names. The invented spellings are kept only as trailing aliases
 * so the fixtures still resolve. scripts/validate-integration.js now checks Search.js against
 * SearchResponse/CitationResponse, which is why this was found at all — the model was absent from
 * MODEL_TO_DTO, so the DTO check had never looked at it.
 */
export function normalizeCitation(raw = {}) {
  const c = obj(raw, null);
  if (!c) return null;
  const rawId = pick(c, ['chunkId', 'id']);
  const out = {
    chunkId: rawId === null ? null : String(rawId),
    documentId: str(pick(c, ['documentId']), null),

    // What kind of source this is (the card's heading). Null renders as blank rather than a guess.
    sourceType: str(pick(c, ['sourceType']), null),
    // Why the retriever returned this chunk — shown as the card's footnote.
    retrievalReason: str(pick(c, ['retrievalReason']), null),

    // The relevance score. null when absent, NEVER 0: a citation whose score we do not know and a
    // citation the retriever scored 0.0 are opposite claims about the evidence under a legal answer.
    // CitationCard renders null as "skor —".
    relevanceScore: num(pick(c, ['relevanceScore', 'score']), null),

    // The cited text itself. A citation with no text is useless, so the legacy spellings stay as
    // fallbacks rather than rendering an empty card.
    citationText: str(pick(c, ['citationText', 'snippet', 'text', 'content']), null),

    sourceObjectKey: str(pick(c, ['sourceObjectKey', 'sourceUri']), null),
    chunkIndex: num(pick(c, ['chunkIndex']), null),
  };
  return withExtras(out, c, CITATION_CONSUMED);
}

export const CitationNormalizer = makeNormalizer(normalizeCitation);

const CONSUMED = [
  'queryId', 'status', 'intent', 'normalizedQuery', 'contextText', 'answer', 'answerText',
  'citations', 'results', 'hits', 'items', 'groundingLevel', 'grounding', 'groundingOverallScore',
  'retrievedChunkCount', 'estimatedTokenCount', 'contextTruncated', 'processingTimeMs', 'tookMs',
  'errorMessage',
];

/**
 * A search result (SearchResponse).
 *
 * Sprint 6 aligned this with the record. It was modelling three fields the backend never sends
 * (`answer`, `retrievalMode`, `degraded`) and ignoring six it does (`status`, `contextText`,
 * `groundingOverallScore`, `estimatedTokenCount`, `contextTruncated`, `errorMessage`) — a normalizer
 * describing an imagined API alongside the real one.
 *
 * The corrections that carry meaning:
 *
 *   answer -> contextText     the synthesized text has a real name. `answer` was always null, so the
 *                             "citation variant" this file's header describes was not a variant at
 *                             all — it was the ONLY thing that could ever happen.
 *   degraded -> status        SearchResponse.error() sends status:"ERROR", groundingLevel:UNGROUNDED
 *                             and an errorMessage. `degraded` guessed at fields that do not exist and
 *                             so was always false — the app could not tell a failed search from an
 *                             empty one, and rendered both as "Tidak ada hasil".
 *   retrievalMode             genuinely client-side. The server does not echo the mode back, so this
 *                             is just what we asked for; it no longer pretends to read a field.
 *
 * @param {any} raw          the unwrapped SearchResponse payload
 * @param {string} [requestedMode]  'semantic' | 'structured' — what the client asked for.
 */
export function normalizeSearchResult(raw = {}, requestedMode = null) {
  const source = obj(raw, {}) || {};

  // `citations` is the real field; the others cover a retriever that names its output differently.
  // toList also unwraps a paged {items:[...]} without special-casing.
  const citations = CitationNormalizer.list(
    toList(source, ['citations', 'results', 'hits', 'items']),
  );

  const status = str(pick(source, ['status']), null);

  const out = {
    queryId: str(pick(source, ['queryId']), null),
    // "SUCCESS" | "ERROR" — the search's own verdict, distinct from the HTTP envelope's.
    status,
    intent: str(pick(source, ['intent']), null),
    normalizedQuery: str(pick(source, ['normalizedQuery']), null),

    // ALWAYS an array. This single guarantee is what makes an empty or partial result render instead
    // of crashing: the screen does `result.citations ?? []` then `.length`, and a null here would be
    // indistinguishable from "no result at all".
    citations,

    // null when the server did not compute one. The screen renders "Grounding: —" for null, so an
    // absent grounding degrades to a dash rather than a false claim.
    groundingLevel: str(pick(source, ['groundingLevel', 'grounding']), null),
    // The raw 0..1 score behind the level. null, never 0 — 0 is what the backend sends on ERROR.
    groundingOverallScore: num(pick(source, ['groundingOverallScore']), null),

    // Server count preferred: it can exceed citations.length when the server truncates to maxResults,
    // and that difference is real information ("we found more than we show").
    retrievedChunkCount: num(pick(source, ['retrievedChunkCount']), null) ?? citations.length,
    estimatedTokenCount: num(pick(source, ['estimatedTokenCount']), null),
    processingTimeMs: num(pick(source, ['processingTimeMs', 'tookMs']), null),

    // The assembled context — the synthesized prose backing the citations. `answer`/`answerText`
    // remain as trailing aliases for the fixtures only.
    contextText: str(pick(source, ['contextText', 'answer', 'answerText']), null),
    // The context hit the token budget and was cut. Tri-state: absent is not "false".
    contextTruncated: triBool(pick(source, ['contextTruncated'])),

    // Present only on a failed search, and the reason it failed.
    errorMessage: str(pick(source, ['errorMessage']), null),
    // Derived from the server's OWN verdict rather than from a guessed field. A search that errored
    // is not a search that found nothing, and the two must not render identically.
    failed: status === 'ERROR',

    // Client-side only: the server does not echo the mode back. Recorded so the screen can label what
    // it asked for, never presented as something the backend confirmed.
    requestedMode,
  };
  return withExtras(out, source, CONSUMED);
}

export const SearchNormalizer = makeNormalizer(normalizeSearchResult);
