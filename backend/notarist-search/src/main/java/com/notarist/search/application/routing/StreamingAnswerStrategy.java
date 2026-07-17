package com.notarist.search.application.routing;

/**
 * A strategy that can emit its answer token-by-token.
 *
 * <p>Only LLM-backed strategies implement this — a SQL {@code COUNT} has no tokens to stream, it has
 * a number. The router handles that case by emitting the finished deterministic answer as a single
 * token, so the streaming transport stays uniform even though the engines are not.
 */
public interface StreamingAnswerStrategy extends AnswerStrategy {

    AnswerResult executeStreaming(ClassifiedQuery query, AnswerRequest request, AnswerTokenSink sink);
}
