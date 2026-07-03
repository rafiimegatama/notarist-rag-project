package com.notarist.search.application.port.out;

import com.notarist.core.domain.valueobject.ClassificationLevel;

import java.util.List;
import java.util.UUID;

/**
 * Output port for Qdrant dense vector search.
 * Implemented by QdrantSearchAdapter (stub in Phase 3).
 * Real bge-m3 query encoding + Qdrant HTTP call deferred to Phase 2C/4.
 */
public interface VectorSearchPort {

    List<VectorSearchResult> search(
            float[] queryVector,
            UUID tenantId,
            ClassificationLevel maxClassificationLevel,
            String documentTypeFilter,
            int limit,
            float minScore);

    record VectorSearchResult(
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
            double cosineScore
    ) {}
}
