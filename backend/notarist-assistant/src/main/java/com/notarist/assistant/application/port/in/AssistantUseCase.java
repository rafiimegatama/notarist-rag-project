package com.notarist.assistant.application.port.in;

import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.application.command.AssistantCommand;

public interface AssistantUseCase {

    /** Blocking: runs the full RAG pipeline and returns the finished response. */
    AssistantResponse ask(AssistantCommand command);

    /**
     * Same pipeline as {@link #ask}, but the LLM is invoked on its token-level streaming path:
     * each token is pushed to {@code sink} as the model produces it. The finished
     * {@link AssistantResponse} (citations, confidence, guard warnings, follow-ups) is still
     * returned so the caller can emit the trailing SSE events after the answer.
     *
     * <p>{@link StreamSink#onStart} fires with the traceId before any inference begins; that
     * traceId is the handle for {@link #cancelStream}.
     */
    AssistantResponse askStreaming(AssistantCommand command, StreamSink sink);

    /**
     * Cancels the in-flight LLM inference for a traceId (SSE client disconnected, emitter timed
     * out, or the connection errored). Inference is a scarce single-threaded resource — without
     * this, a doomed request keeps occupying it. Idempotent; false if nothing was cancellable.
     */
    boolean cancelStream(String traceId);

    /** Callback surface for token-level streaming. */
    interface StreamSink {

        /** Invoked once, before retrieval and inference, with the traceId used for cancellation. */
        void onStart(String traceId);

        /** Invoked for each token emitted by the LLM, in order. Must not throw. */
        void onToken(String token);
    }
}
