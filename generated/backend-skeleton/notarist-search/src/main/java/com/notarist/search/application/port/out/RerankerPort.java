package com.notarist.search.application.port.out;

import com.notarist.search.domain.model.RetrievalResult;

import java.util.List;

/** Port for BGE cross-encoder reranker sidecar — :8083. 15s timeout, 2x retry. */
public interface RerankerPort {
    List<RetrievalResult> rerank(String queryText, List<RetrievalResult> candidates, int topK);
}
