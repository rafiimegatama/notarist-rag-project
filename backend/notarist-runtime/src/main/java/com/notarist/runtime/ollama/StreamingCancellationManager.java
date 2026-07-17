package com.notarist.runtime.ollama;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages on-demand cancellation of active Ollama streaming responses.
 *
 * When an SSE client disconnects, AssistantController calls cancel(traceId).
 * OllamaRuntimeAdapter checks isCancelled() in its token-emission loop.
 *
 * Lifecycle: register → (tokens emitted) → [optional cancel] → deregister
 * Cancelled traceIds are retained for 30s to handle late arrives; GC relies on deregister.
 */
@Component
public class StreamingCancellationManager {

    private static final Logger log = LoggerFactory.getLogger(StreamingCancellationManager.class);

    private final ConcurrentHashMap<String, Runnable> cancelCallbacks  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean>  cancelledFlags   = new ConcurrentHashMap<>();
    private final Counter                             cancellationCounter;

    public StreamingCancellationManager(MeterRegistry meterRegistry) {
        this.cancellationCounter = Counter.builder("notarist.runtime.streaming.cancellation")
                .description("Number of Ollama streaming responses cancelled")
                .register(meterRegistry);
    }

    /**
     * Opens a cancellable scope for traceId BEFORE the inference call starts.
     *
     * <p>Inference is queued behind a single thread (InferenceQueueIsolation), so there is a
     * window between "request accepted" and "OkHttp call created" in which the client may
     * already have disconnected. Opening the scope up front means {@link #cancel} taken during
     * that window is remembered, and {@link #register} honours it immediately instead of
     * letting a doomed inference run to completion.
     *
     * <p>Must be paired with {@link #deregister}.
     */
    public void open(String traceId) {
        cancelledFlags.putIfAbsent(traceId, false);
        log.debug("StreamingCancellationManager: opened traceId={}", traceId);
    }

    /**
     * Registers a cancel callback for the given traceId.
     * The callback must close/abort the underlying OkHttp response body.
     *
     * <p>If the stream was already cancelled while queued, the callback runs at once.
     */
    public void register(String traceId, Runnable cancelCallback) {
        cancelCallbacks.put(traceId, cancelCallback);
        Boolean prior = cancelledFlags.putIfAbsent(traceId, false);

        if (Boolean.TRUE.equals(prior)) {
            log.info("StreamingCancellationManager: traceId={} was cancelled before inference started — aborting immediately", traceId);
            runCallback(traceId, cancelCallback);
            return;
        }
        log.debug("StreamingCancellationManager: registered traceId={}", traceId);
    }

    /**
     * Cancels the stream. Marks the flag and invokes the registered callback, if any.
     * No-op if traceId has no open scope (never opened, or already finished) or is already cancelled.
     */
    public boolean cancel(String traceId) {
        // replace(k, false, true) only succeeds when the scope is open and not yet cancelled —
        // it will NOT resurrect an entry for a traceId that has already been deregistered.
        if (!cancelledFlags.replace(traceId, false, true)) return false;

        Runnable callback = cancelCallbacks.get(traceId);
        if (callback != null) runCallback(traceId, callback);

        cancellationCounter.increment();
        log.info("StreamingCancellationManager: CANCELLED traceId={}", traceId);
        return true;
    }

    private void runCallback(String traceId, Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            log.warn("StreamingCancellationManager: cancel callback threw for traceId={}: {}", traceId, e.getMessage());
        }
    }

    /**
     * Called by OllamaRuntimeAdapter in its token loop to check early exit.
     */
    public boolean isCancelled(String traceId) {
        return Boolean.TRUE.equals(cancelledFlags.get(traceId));
    }

    /**
     * Must be called when streaming completes (normally or on error) to release resources.
     */
    public void deregister(String traceId) {
        cancelCallbacks.remove(traceId);
        cancelledFlags.remove(traceId);
        log.debug("StreamingCancellationManager: deregistered traceId={}", traceId);
    }

    public int activeStreamCount() {
        return cancelCallbacks.size();
    }
}
