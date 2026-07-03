package com.notarist.search.domain.model;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable retrieval result for a single chunk.
 * Carries both scores and retrieval provenance.
 * Every chunk MUST have at least one RetrievalReason before being returned.
 */
public record RetrievedChunk(
        String chunkId,
        DocumentId documentId,
        UUID tenantId,
        JenisDokumen documentType,
        ClassificationLevel classificationLevel,
        int chunkIndex,
        String sectionTitle,
        Integer pageNumber,
        String text,
        String sourceObjectKey,
        double keywordScore,
        double semanticScore,
        double rerankScore,
        double fusedScore,
        Set<RetrievalReason> retrievalReasons
) {
    public RetrievedChunk {
        if (chunkId == null || chunkId.isBlank()) throw new IllegalArgumentException("chunkId required");
        if (documentId == null) throw new IllegalArgumentException("documentId required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (retrievalReasons == null || retrievalReasons.isEmpty())
            throw new IllegalArgumentException("at least one RetrievalReason required");
        retrievalReasons = Set.copyOf(retrievalReasons);
    }

    public RetrievedChunk withRerankScore(double newRerankScore) {
        return new RetrievedChunk(
                chunkId, documentId, tenantId, documentType, classificationLevel,
                chunkIndex, sectionTitle, pageNumber, text, sourceObjectKey,
                keywordScore, semanticScore, newRerankScore, fusedScore, retrievalReasons);
    }

    public RetrievedChunk withAdditionalReason(RetrievalReason reason) {
        Set<RetrievalReason> updated = new HashSet<>(retrievalReasons);
        updated.add(reason);
        return new RetrievedChunk(
                chunkId, documentId, tenantId, documentType, classificationLevel,
                chunkIndex, sectionTitle, pageNumber, text, sourceObjectKey,
                keywordScore, semanticScore, rerankScore, fusedScore, updated);
    }

    public double effectiveScore() {
        return rerankScore > 0 ? rerankScore : fusedScore;
    }
}
