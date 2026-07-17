package com.notarist.search.application.routing;

/**
 * Receives tokens from a streaming answer.
 *
 * <p>Deliberately engine-agnostic. The caller attaches a sink and receives tokens; it is never told
 * whether those tokens came from a language model or were emitted in one piece from a SQL result. A
 * factual answer streams as a single token — the transport behaves identically, so the SSE contract
 * the mobile app relies on does not change with the routing decision.
 */
public interface AnswerTokenSink {

    /** Invoked for each token, in order. Must not throw. */
    void onToken(String token);
}
