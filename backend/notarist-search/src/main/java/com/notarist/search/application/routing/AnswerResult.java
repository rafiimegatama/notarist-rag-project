package com.notarist.search.application.routing;

import java.util.List;
import java.util.Map;

/**
 * The uniform result of any execution strategy — SQL or RAG. The caller (the assistant) cannot tell
 * from the type which engine produced it, which is the point: the assistant is not allowed to know
 * whether an answer came from SQL, BM25, a vector index or a language model.
 *
 * <p>{@link AnswerMetadata} carries the audit facts required of every answer: which strategy ran,
 * how long it took, and — critically — whether an LLM was invoked at all. That last flag is what
 * makes the "no LLM for facts" rule <em>verifiable after the fact</em>, not merely asserted.
 */
public record AnswerResult(
        String answerText,
        List<AnswerCitation> citations,
        /** Structured payload for factual answers (counts, breakdowns, row data). Empty for RAG. */
        Map<String, Object> structuredData,
        /** False when the question is well-formed but the backing data does not exist yet. */
        boolean supported,
        List<String> warnings,
        float groundingScore,
        String confidence,
        boolean downgraded,
        AnswerMetadata metadata
) {
    public AnswerResult {
        citations      = citations == null ? List.of() : List.copyOf(citations);
        structuredData = structuredData == null ? Map.of() : Map.copyOf(structuredData);
        warnings       = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** A deterministic answer computed from the database. Never involves an LLM. */
    public static AnswerResult fromSql(
            String answerText,
            Map<String, Object> structuredData,
            String strategy,
            long executionMs) {

        return new AnswerResult(
                answerText, List.of(), structuredData, true, List.of(),
                1.0f, "HIGH", false,
                new AnswerMetadata(strategy, executionMs, false, true, 0, 0));
    }

    /**
     * The question is understood and is a legitimate factual question — but the data required to
     * answer it does not exist in the system yet (e.g. bundle/case/approval tables, which arrive in
     * a later sprint).
     *
     * <p>This is deliberately NOT a fallback to the LLM. Answering "has bundle ABC been delivered?"
     * from a language model reading document chunks would produce a fluent, confident, unfounded
     * answer about a legal delivery — the precise failure mode this router exists to prevent. Saying
     * "I cannot answer this yet" is the correct behaviour.
     */
    public static AnswerResult unsupported(String reason, String strategy, long executionMs) {
        return new AnswerResult(
                reason, List.of(), Map.of(), false, List.of(reason),
                0f, "INSUFFICIENT", false,
                new AnswerMetadata(strategy, executionMs, false, true, 0, 0));
    }

    /** An answer synthesised by the RAG pipeline over retrieved, cited chunks. */
    public static AnswerResult fromRag(
            String answerText,
            List<AnswerCitation> citations,
            List<String> warnings,
            float groundingScore,
            String confidence,
            boolean downgraded,
            String strategy,
            long executionMs,
            int documentsRetrieved) {

        return new AnswerResult(
                answerText, citations, Map.of(), true, warnings,
                groundingScore, confidence, downgraded,
                new AnswerMetadata(strategy, executionMs, true, false,
                        documentsRetrieved, citations == null ? 0 : citations.size()));
    }

    /** A ranked list of retrieved documents, with no LLM synthesis. */
    public static AnswerResult fromRetrieval(
            String answerText,
            List<AnswerCitation> citations,
            String strategy,
            long executionMs,
            int documentsRetrieved) {

        return new AnswerResult(
                answerText, citations, Map.of(), true, List.of(),
                citations == null || citations.isEmpty() ? 0f : 1.0f,
                citations == null || citations.isEmpty() ? "INSUFFICIENT" : "HIGH",
                false,
                new AnswerMetadata(strategy, executionMs, false, false,
                        documentsRetrieved, citations == null ? 0 : citations.size()));
    }

    /** Audit metadata attached to every answer (Task 7). */
    public record AnswerMetadata(
            String strategyUsed,
            long executionTimeMs,
            boolean llmInvoked,
            boolean sqlInvoked,
            int documentsRetrieved,
            int citationsCount
    ) {}
}
