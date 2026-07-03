package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.ingest.application.port.out.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub adapter for Qdrant on configured URL.
 * No-ops all operations. Replace with real Qdrant client calls in Phase 2C.
 */
@Component
public class VectorIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexAdapter.class);

    private final String qdrantUrl;

    public VectorIndexAdapter(
            @Value("${notarist.qdrant.url:http://localhost:6333}") String qdrantUrl) {
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
