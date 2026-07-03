package com.notarist.assistant.api.response;

/** API-layer citation record. Populated from retrieval metadata, never from LLM output. */
public record CitationDto(
        String chunkId,
        String documentId,
        String documentType,
        String classificationLevel,
        String sectionTitle,
        int chunkIndex,
        String chunkText,
        String sourceObjectKey,
        double relevanceScore
) {}
