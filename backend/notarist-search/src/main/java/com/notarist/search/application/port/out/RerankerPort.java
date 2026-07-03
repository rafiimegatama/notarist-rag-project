package com.notarist.search.application.port.out;

import java.util.List;

/**
 * Output port for cross-encoder reranking.
 * Implemented by RerankerAdapter (identity stub in Phase 3).
 * Real cross-encoder HTTP call deferred to Phase 4.
 */
public interface RerankerPort {

    List<RerankResult> rerank(String query, List<RerankCandidate> candidates);

    record RerankCandidate(String chunkId, String text) {}

    record RerankResult(String chunkId, double rerankScore) {}
}
