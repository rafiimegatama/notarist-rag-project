package com.notarist.ingest.domain.model;

import com.notarist.core.domain.policy.OcrReviewStatus;
import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.time.Instant;
import java.util.UUID;

/**
 * A document chunk produced by the chunking stage, persisted in chunk_index (Postgres)
 * and carried through the embedding and indexing stages.
 *
 * embedding/embeddingModel/embeddedAt are null until the embedding stage populates them.
 */
public record ChunkMetadata(
        ChunkId chunkId,
        IngestionId ingestionId,
        DocumentId documentId,
        UUID tenantId,
        JenisDokumen documentType,
        ClassificationLevel classificationLevel,
        int chunkIndex,
        int startOffset,
        int endOffset,
        int tokenCount,
        String chunkStrategy,
        int overlapTokens,
        String sectionTitle,
        String pasalRef,
        Integer pageNumber,
        String chunkText,
        String sourceObjectKey,
        float ocrConfidence,
        OcrReviewStatus reviewStatus,
        boolean searchable,
        float[] embedding,
        String embeddingModel,
        Instant embeddedAt,
        Instant createdAt
) {
    public ChunkMetadata {
        if (chunkId == null) throw new IllegalArgumentException("chunkId required");
        if (ingestionId == null) throw new IllegalArgumentException("ingestionId required");
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (documentType == null) throw new IllegalArgumentException("documentType required");
        if (classificationLevel == null) throw new IllegalArgumentException("classificationLevel required");
        if (chunkText == null || chunkText.isBlank()) throw new IllegalArgumentException("chunkText required");
        if (tokenCount < 0) throw new IllegalArgumentException("tokenCount must be >= 0");
        if (reviewStatus == null) throw new IllegalArgumentException("reviewStatus required");
        if (createdAt == null) createdAt = Instant.now();
    }

    /** Returns a copy of this chunk with the given embedding attached. */
    public ChunkMetadata withEmbedding(float[] embedding, String embeddingModel, Instant embeddedAt) {
        return new ChunkMetadata(
                chunkId, ingestionId, documentId, tenantId, documentType, classificationLevel,
                chunkIndex, startOffset, endOffset, tokenCount, chunkStrategy, overlapTokens,
                sectionTitle, pasalRef, pageNumber, chunkText, sourceObjectKey,
                ocrConfidence, reviewStatus, searchable, embedding, embeddingModel, embeddedAt, createdAt);
    }
}
