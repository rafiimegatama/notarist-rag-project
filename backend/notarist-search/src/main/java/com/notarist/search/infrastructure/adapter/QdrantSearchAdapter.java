package com.notarist.search.infrastructure.adapter;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.search.application.port.out.VectorSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Qdrant vector search stub — Phase 3.
 * Real bge-m3 query encoding + Qdrant HTTP integration deferred to Phase 4.
 * Returns empty list so BM25 results drive retrieval during this phase.
 */
// @Component deactivated: superseded by QdrantSearchAdapter in notarist-infra (Phase 5)
public class QdrantSearchAdapter implements VectorSearchPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantSearchAdapter.class);

    @Override
    public List<VectorSearchResult> search(
            float[] queryVector,
            UUID tenantId,
            ClassificationLevel maxClassificationLevel,
            String documentTypeFilter,
            int limit,
            float minScore) {
        log.debug("QdrantSearchAdapter stub — Phase 3, returning empty. tenantId={}", tenantId);
        return List.of();
    }
}
