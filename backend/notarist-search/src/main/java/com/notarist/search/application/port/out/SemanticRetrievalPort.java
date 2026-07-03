package com.notarist.search.application.port.out;

import com.notarist.search.domain.model.RetrievalResult;
import com.notarist.search.domain.model.SearchQuery;

import java.util.List;

/** Port for Qdrant cosine similarity retrieval. Returns TOP-20 candidates. */
public interface SemanticRetrievalPort {
    float[] generateQueryEmbedding(String queryText);
    List<RetrievalResult> searchCosine(SearchQuery query, float[] queryEmbedding, int topK);
}
