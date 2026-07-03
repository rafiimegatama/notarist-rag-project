package com.notarist.search.api.request;

import jakarta.validation.constraints.*;

public record HybridSearchRequest(
    @NotBlank @Size(min = 1, max = 500) String queryText,
    String intent,
    SearchFiltersRequest filters,
    @Min(1) @Max(20) Integer topK,
    Boolean includeScores
) {
    public HybridSearchRequest {
        intent = (intent == null) ? "HYBRID" : intent;
        topK = (topK == null) ? 5 : topK;
        includeScores = (includeScores == null) ? false : includeScores;
    }

    public record SearchFiltersRequest(
        String documentType,
        String jenisAkta,
        String classificationLevel,
        java.util.UUID notarisId,
        String dateFrom,
        String dateTo
    ) {}
}
