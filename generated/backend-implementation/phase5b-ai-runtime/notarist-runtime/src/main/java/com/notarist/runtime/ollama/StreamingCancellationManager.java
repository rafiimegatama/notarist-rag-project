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
     * Registers a cancel callback for the given traceId.
     * The callback must close/abort the underlying OkHttp response body.
     */
    public void register(String traceId, Runnable cancelCallback) {
        cancelCallbacks.put(traceId, cancelCallback);
        cancelledFlags.put(traceId, false);
        log.debug("StreamingCancellationManager: registered traceId={}", traceId);
    }

    /**
     * Cancels the active stream. Invokes the registered callback and marks the flag.
     * No-op if traceId is unknown or already cancelled.
     */
    public boolean cancel(String traceId) {
        Boolean already = cancelledFlags.get(traceId);
        if (already == null || already) return false;

        cancelledFlags.put(traceId, true);
        Runnable callback = cancelCallbacks.get(traceId);
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                log.warn("StreamingCancellationManager: cancel callback threw for traceId={}: {}", traceId, e.getMessage());
            }
        }

        cancellationCounter.increment();
        log.info("StreamingCancellationManager: CANCELLED traceId={}", traceId);
        return true;
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
