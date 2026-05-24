package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.List;
import java.util.UUID;

/**
 * Output port for Qdrant vector index operations.
 * Implemented by VectorIndexAdapter (no-op stub in Phase 2).
 * Real Qdrant client upsert deferred to Phase 2C when real vectors are available.
 */
public interface VectorIndexPort {

    void upsertChunks(List<IndexableChunk> chunks);

    void deleteByDocumentId(DocumentId documentId);

    record IndexableChunk(
            String chunkId,
            DocumentId documentId,
            UUID tenantId,
            float[] vector,
            ChunkPayload payload
    ) {}

    record ChunkPayload(
            JenisDokumen documentType,
            ClassificationLevel classificationLevel,
            String sectionTitle,
            int chunkIndex,
            String text,
            String sourceObjectKey
    ) {}
}
