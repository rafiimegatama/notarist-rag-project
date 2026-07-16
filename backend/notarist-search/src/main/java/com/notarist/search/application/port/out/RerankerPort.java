package com.notarist.search.application.port.out;

import java.util.List;

/**
 * Output port for cross-encoder reranking.
 * Implemented by RegistryRerankerPort (notarist-runtime), which routes to the active
 * RerankerProvider (none = passthrough, or crossencoder = bge-reranker-v2-m3 HTTP sidecar).
 */
public interface RerankerPort {

    List<RerankResult> rerank(String query, List<RerankCandidate> candidates);

    record RerankCandidate(String chunkId, String text) {}

    record RerankResult(String chunkId, double rerankScore) {}
}
