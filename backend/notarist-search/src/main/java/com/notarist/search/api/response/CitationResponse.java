package com.notarist.search.api.response;

import com.notarist.search.domain.model.CitationEntry;

import java.util.UUID;

public record CitationResponse(
        String chunkId,
        UUID documentId,
        String sourceType,
        String retrievalReason,
        double relevanceScore,
        String citationText,
        String chunkText,
        String sourceObjectKey,
        int chunkIndex
) {
    public static CitationResponse from(CitationEntry entry) {
        return new CitationResponse(
                entry.chunkId(),
                entry.documentId().value(),
                entry.sourceType().name(),
                entry.retrievalReason().name(),
                entry.relevanceScore(),
                entry.citationText(),
                entry.chunkText(),
                entry.sourceObjectKey(),
                entry.chunkIndex());
    }
}
