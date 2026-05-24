package com.notarist.assistant.application.port.out;

import com.notarist.assistant.domain.model.AnswerConfidence;

import java.util.List;
import java.util.UUID;

/**
 * Output port for retrieval.
 * Decouples assistant from Phase 3 search internals.
 * Implemented by SearchAdapter (stub in Phase 4; real call to notarist-search in Phase 5).
 */
public interface SearchPort {

    SearchResult search(AssistantSearchRequest request);

    record AssistantSearchRequest(
            String query,
            UUID tenantId,
            UUID userId,
            String maxClassificationLevel,
            String documentTypeFilter,
            int maxResults,
            int contextTokenBudget,
            UUID correlationId
    ) {}

    record RetrievedChunkDto(
            String chunkId,
            String documentId,
            String tenantId,
            String documentType,
            String classificationLevel,
            int chunkIndex,
            String sectionTitle,
            Integer pageNumber,
            String chunkText,
            String sourceObjectKey,
            double relevanceScore
    ) {}

    record SearchResult(
            List<RetrievedChunkDto> chunks,
            AnswerConfidence groundingConfidence,
            float groundingScore,
            UUID retrievalSnapshotId,
            int totalCandidates
    ) {
        public static SearchResult empty() {
            return new SearchResult(List.of(), AnswerConfidence.INSUFFICIENT, 0f, UUID.randomUUID(), 0);
        }
    }
}
