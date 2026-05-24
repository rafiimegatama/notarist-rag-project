package com.notarist.search.infrastructure.adapter;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.search.application.port.out.VectorSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;

/**
 * Qdrant vector search stub — Phase 3.
 * Returns empty list so BM25 results drive retrieval during pipeline testing.
 *
 * PHASE 6A.2-FIX: @Component REMOVED.
 * Production bean: com.notarist.infra.qdrant.QdrantSearchAdapter (notarist-infra).
 * Test usage: declare as @Bean in @TestConfiguration.
 */
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
