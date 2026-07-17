package com.notarist.runtime.degradation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-AI-runtime degradation state.
 *
 * Higher-level than Phase 5A DegradedModeRegistry (which tracks infra services).
 * This tracks AI-specific runtimes and derives the system's OperationMode.
 *
 * OperationMode derivation:
 *   FULL                   — all runtimes healthy
 *   DEGRADED_NO_LLM        — OLLAMA degraded, others healthy (keyword search still works)
 *   DEGRADED_NO_EMBEDDING  — EMBEDDING degraded (no new ingestion; existing index works)
 *   MINIMAL_KEYWORD_ONLY   — both OLLAMA and EMBEDDING degraded
 */
@Component
public class RuntimeDegradationManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDegradationManager.class);

    public enum AiRuntime { OCR, OLLAMA, EMBEDDING, RERANKER, NER }

    public enum OperationMode {
        FULL,
        DEGRADED_NO_LLM,
        DEGRADED_NO_EMBEDDING,
        MINIMAL_KEYWORD_ONLY
    }

    public record DegradedState(
            boolean degraded,
            Instant degradedSince,
            String reason,
            int consecutiveFailures
    ) {
        static DegradedState healthy() {
            return new DegradedState(false, null, null, 0);
        }

        DegradedState fail(String failReason) {
            return new DegradedState(true,
                    degraded ? degradedSince : Instant.now(),
                    failReason,
                    consecutiveFailures + 1);
        }

        DegradedState recover() {
            return healthy();
        }
    }

    private final ConcurrentHashMap<AiRuntime, DegradedState> states = new ConcurrentHashMap<>();

    public RuntimeDegradationManager(MeterRegistry meterRegistry) {
        for (AiRuntime runtime : AiRuntime.values()) {
            states.put(runtime, DegradedState.healthy());
            String name = runtime.name().toLowerCase();
            Gauge.builder("notarist.runtime.degraded." + name,
                            states, s -> s.get(runtime).degraded() ? 1.0 : 0.0)
                    .description("1=degraded, 0=healthy for AI runtime: " + name)
                    .register(meterRegistry);
        }
    }

    public void markRuntime(AiRuntime runtime, boolean failed, String reason) {
        states.compute(runtime, (k, current) -> {
            if (current == null) current = DegradedState.healthy();
            DegradedState next = failed ? current.fail(reason) : current.recover();
            if (failed && !current.degraded()) {
                log.warn("RuntimeDegradationManager: {} DEGRADED — reason={}", runtime, reason);
            } else if (!failed && current.degraded()) {
                log.info("RuntimeDegradationManager: {} RECOVERED after {} failures", runtime, current.consecutiveFailures());
            }
            return next;
        });
    }

    public boolean isDegraded(AiRuntime runtime) {
        DegradedState state = states.get(runtime);
        return state != null && state.degraded();
    }

    public DegradedState getState(AiRuntime runtime) {
        return states.getOrDefault(runtime, DegradedState.healthy());
    }

    public OperationMode getActiveMode() {
        boolean ollamaDegraded    = isDegraded(AiRuntime.OLLAMA);
        boolean embeddingDegraded = isDegraded(AiRuntime.EMBEDDING);

        if (ollamaDegraded && embeddingDegraded) return OperationMode.MINIMAL_KEYWORD_ONLY;
        if (ollamaDegraded)                       return OperationMode.DEGRADED_NO_LLM;
        if (embeddingDegraded)                    return OperationMode.DEGRADED_NO_EMBEDDING;
        return OperationMode.FULL;
    }

    public Map<AiRuntime, DegradedState> snapshot() {
        return Map.copyOf(states);
    }
}
