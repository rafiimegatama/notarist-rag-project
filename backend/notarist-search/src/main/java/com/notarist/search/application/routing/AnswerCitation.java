package com.notarist.search.application.routing;

/**
 * A citation carried across the router boundary. Populated from retrieval metadata only — never
 * from LLM output, mirroring the existing rule in the assistant's {@code CitationDto}.
 */
public record AnswerCitation(
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
