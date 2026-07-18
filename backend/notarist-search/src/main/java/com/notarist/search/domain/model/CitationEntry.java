package com.notarist.search.domain.model;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;

/**
 * Resolved citation for a retrieved chunk.
 * Built BEFORE context assembly — citations must exist before any LLM invocation.
 */
public record CitationEntry(
        String chunkId,
        DocumentId documentId,
        JenisDokumen sourceType,
        RetrievalReason retrievalReason,
        double relevanceScore,
        String citationText,
        String chunkText,
        String sourceObjectKey,
        int chunkIndex
) {
    public CitationEntry {
        if (chunkId == null || chunkId.isBlank()) throw new IllegalArgumentException("chunkId required");
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (retrievalReason == null) throw new IllegalArgumentException("retrievalReason required");
    }
}
