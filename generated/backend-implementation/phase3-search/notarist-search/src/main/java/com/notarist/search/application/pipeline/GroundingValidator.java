package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.GroundingScore;
import com.notarist.search.domain.model.RetrievalReason;
import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes grounding score from retrieved chunks.
 * Grounding score is computed BEFORE context assembly — determines trustworthiness
 * of the retrieval set before it is presented to an LLM.
 */
@Service
public class GroundingValidator {

    public GroundingScore validate(String normalizedQuery, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) return GroundingScore.ungrounded();

        float retrievalCoverage  = computeRetrievalCoverage(normalizedQuery, chunks);
        float citationDensity    = computeCitationDensity(chunks);
        float semanticAgreement  = computeSemanticAgreement(chunks);
        float answerTraceability = computeAnswerTraceability(chunks);

        return GroundingScore.compute(retrievalCoverage, citationDensity, semanticAgreement, answerTraceability);
    }

    /** Fraction of non-trivial query tokens found anywhere in retrieved chunk texts. */
    private float computeRetrievalCoverage(String normalizedQuery, List<RetrievedChunk> chunks) {
        String[] tokens = normalizedQuery.split("\\s+");
        if (tokens.length == 0) return 0f;
        String combined = chunks.stream().map(RetrievedChunk::text)
                .collect(Collectors.joining(" ")).toLowerCase();
        long matched = Arrays.stream(tokens)
                .filter(t -> t.length() > 2 && combined.contains(t))
                .count();
        return Math.min(1f, (float) matched / tokens.length);
    }

    /** Number of distinct source documents in results, normalized to a max of 5. */
    private float computeCitationDensity(List<RetrievedChunk> chunks) {
        Set<String> distinct = chunks.stream()
                .map(c -> c.documentId().value().toString())
                .collect(Collectors.toSet());
        return Math.min(1f, distinct.size() / 5.0f);
    }

    /** Proportion of chunks that have at least one semantic retrieval signal. */
    private float computeSemanticAgreement(List<RetrievedChunk> chunks) {
        long semanticCount = chunks.stream()
                .filter(c -> c.retrievalReasons().contains(RetrievalReason.SEMANTIC_MATCH))
                .count();
        return (float) semanticCount / chunks.size();
    }

    /** Proportion of chunks with a non-blank sourceObjectKey (traceable to a MinIO object). */
    private float computeAnswerTraceability(List<RetrievedChunk> chunks) {
        long traceable = chunks.stream()
                .filter(c -> c.sourceObjectKey() != null && !c.sourceObjectKey().isBlank())
                .count();
        return (float) traceable / chunks.size();
    }
}
