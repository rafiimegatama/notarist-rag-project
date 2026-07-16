package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.RagPort;
import com.notarist.search.application.routing.AnswerRequest;
import com.notarist.search.application.routing.AnswerResult;

/** Shared translation between the router's request/result shapes and the RAG port's. */
final class RagRequests {

    private RagRequests() {}

    static RagPort.RagRequest of(AnswerRequest request, RagPort.SynthesisMode mode) {
        return new RagPort.RagRequest(
                request.rawQuery(),
                request.tenantId(),
                request.userId(),
                request.maxClassificationLevel().name(),
                request.documentTypeFilter() != null ? request.documentTypeFilter().name() : null,
                request.maxResults(),
                request.contextTokenBudget(),
                request.strictMode(),
                request.traceId(),
                java.util.UUID.fromString(normalizeCorrelation(request)),
                mode);
    }

    /** CorrelationId may be a non-UUID string; the RAG pipeline wants a UUID for its snapshot id. */
    private static String normalizeCorrelation(AnswerRequest request) {
        try {
            return java.util.UUID.fromString(request.correlationId().value()).toString();
        } catch (IllegalArgumentException e) {
            return java.util.UUID.randomUUID().toString();
        }
    }
}

/** Maps a RAG answer back onto the uniform router result. */
final class RagResults {

    private RagResults() {}

    static AnswerResult toAnswerResult(RagPort.RagAnswer answer, String strategyName, long executionMs) {
        return AnswerResult.fromRag(
                answer.answerText(),
                answer.citations(),
                answer.warnings(),
                answer.groundingScore(),
                answer.confidence(),
                answer.downgraded(),
                strategyName,
                executionMs,
                answer.documentsRetrieved());
    }
}
