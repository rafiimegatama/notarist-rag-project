package com.notarist.ingest.domain.model;

import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.domain.valueobject.DocumentId;

import java.time.Instant;

/** Immutable lineage metadata for a document chunk produced by the chunking stage. */
public record ChunkMetadata(
        ChunkId chunkId,
        IngestionId ingestionId,
        DocumentId documentId,
        int chunkIndex,
        int startOffset,
        int endOffset,
        int tokenCount,
        String chunkStrategy,
        int overlapTokens,
        String sectionTitle,
        String pasalRef,
        Integer pageNumber,
        String sourceObjectKey,
        Instant createdAt
) {
    public ChunkMetadata {
        if (chunkId == null) throw new IllegalArgumentException("chunkId required");
        if (ingestionId == null) throw new IllegalArgumentException("ingestionId required");
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (tokenCount < 0) throw new IllegalArgumentException("tokenCount must be >= 0");
        if (createdAt == null) createdAt = Instant.now();
    }
}
