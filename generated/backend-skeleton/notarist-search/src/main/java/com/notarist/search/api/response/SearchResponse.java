package com.notarist.search.api.response;

import java.util.List;

public record SearchResponse(
    String queryText,
    String intent,
    List<RetrievalResultResponse> results,
    int totalCandidates,
    long retrievalMs,
    long rerankMs
) {}
