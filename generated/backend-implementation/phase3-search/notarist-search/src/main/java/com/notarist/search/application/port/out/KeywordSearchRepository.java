package com.notarist.search.application.port.out;

import com.notarist.core.domain.valueobject.ClassificationLevel;

import java.util.List;
import java.util.UUID;

/** Output port for PostgreSQL BM25 full-text keyword retrieval. */
public interface KeywordSearchRepository {

    List<BM25SearchResult> search(
            String normalizedQuery,
            UUID tenantId,
            ClassificationLevel maxClassificationLevel,
            String documentTypeFilter,
            int limit);

    record BM25SearchResult(
            String chunkId,
            String documentId,
            String tenantId,
            String documentType,
            String classificationLevel,
            int chunkIndex,
            String sectionTitle,
            Integer pageNumber,
            String chunkText,
            String sourceObjectKey,
            double bm25Score
    ) {}
}
