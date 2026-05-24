package com.notarist.infra.qdrant;

import com.notarist.core.domain.valueobject.ClassificationLevel;

import java.util.*;

/**
 * Type-safe Qdrant filter builder.
 *
 * Prevents raw JSON construction scattered across adapters.
 * All filter conditions are assembled in one place and serialized
 * as Map<String, Object> which Jackson handles correctly.
 *
 * Usage:
 *   Map<String, Object> filter = QdrantFilterBuilder.must()
 *       .tenantId(tenantId)
 *       .maxClassification(ClassificationLevel.INTERNAL)
 *       .docType("AKTA")
 *       .onlySearchable()
 *       .build();
 */
public class QdrantFilterBuilder {

    private final List<Map<String, Object>> mustClauses = new ArrayList<>();

    public static QdrantFilterBuilder must() {
        return new QdrantFilterBuilder();
    }

    public QdrantFilterBuilder tenantId(UUID tenantId) {
        return matchValue("tenant_id", tenantId.toString());
    }

    public QdrantFilterBuilder maxClassification(ClassificationLevel maxLevel) {
        Map<String, Object> clause = new LinkedHashMap<>();
        clause.put("key", "classification_ordinal");
        clause.put("range", Map.of("lte", maxLevel.ordinal()));
        mustClauses.add(clause);
        return this;
    }

    public QdrantFilterBuilder docType(String docType) {
        if (docType != null && !docType.isBlank()) {
            matchValue("doc_type", docType);
        }
        return this;
    }

    public QdrantFilterBuilder onlySearchable() {
        return matchValue("is_searchable", true);
    }

    public QdrantFilterBuilder matchValue(String key, Object value) {
        Map<String, Object> clause = new LinkedHashMap<>();
        clause.put("key", key);
        clause.put("match", Map.of("value", value));
        mustClauses.add(clause);
        return this;
    }

    /**
     * Returns the filter map ready for inclusion in a Qdrant search/delete request body.
     * Returns null (no filter) if no clauses were added.
     */
    public Map<String, Object> build() {
        if (mustClauses.isEmpty()) return null;
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", List.copyOf(mustClauses));
        return filter;
    }
}
