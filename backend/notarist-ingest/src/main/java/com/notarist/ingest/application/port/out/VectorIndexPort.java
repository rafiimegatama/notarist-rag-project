package com.notarist.ingest.application.port.out;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.List;
import java.util.UUID;

/**
 * Output port for Qdrant vector index operations.
 * Implemented by QdrantIndexAdapter in notarist-infra.
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
            String pasalRef,
            int chunkIndex,
            String text,
            String sourceObjectKey,
            boolean searchable
    ) {}
}
