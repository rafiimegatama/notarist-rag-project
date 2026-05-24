package com.notarist.search.domain.model;

import com.notarist.core.domain.valueobject.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Value object representing a complete search request.
 * Access filters are applied by SearchPolicyService based on caller role.
 */
public record SearchQuery(
    String queryText,
    SearchIntent intent,
    UUID callerUserId,
    UUID callerTenantId,
    ClassificationLevel callerClearance,
    SearchFilters filters,
    int topK
) {
    public SearchQuery {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }

    public record SearchFilters(
        JenisDokumen documentType,
        JenisAkta jenisAkta,
        ClassificationLevel classificationLevel,
        UUID notarisId,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        public static SearchFilters empty() {
            return new SearchFilters(null, null, null, null, null, null);
        }
    }
}
