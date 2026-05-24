package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.ingest.application.port.out.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub adapter for Qdrant vector index.
 * No-ops all operations for pipeline testing.
 *
 * PHASE 6A.2-FIX: @Component REMOVED.
 * Production bean: QdrantIndexAdapter in notarist-infra.
 * Test usage: declare as @Bean in @TestConfiguration.
 */
public class VectorIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexAdapter.class);

    private final String qdrantUrl;

    public VectorIndexAdapter(String qdrantUrl) {
        this.qdrantUrl = qdrantUrl;
    }

    @Override
    public void upsertChunks(List<IndexableChunk> chunks) {
        log.info("[STUB] Qdrant upsertChunks count={} qdrant={}", chunks.size(), qdrantUrl);
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        log.info("[STUB] Qdrant deleteByDocumentId documentId={} qdrant={}",
                documentId.value(), qdrantUrl);
    }
}
