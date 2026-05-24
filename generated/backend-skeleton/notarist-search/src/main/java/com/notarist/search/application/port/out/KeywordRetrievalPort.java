package com.notarist.search.application.port.out;

import com.notarist.search.domain.model.SearchQuery;
import com.notarist.search.domain.model.RetrievalResult;

import java.util.List;

/** Port for PostgreSQL BM25 full-text retrieval. Returns TOP-20 candidates. */
public interface KeywordRetrievalPort {
    List<RetrievalResult> searchBM25(SearchQuery query, int topK);
}
