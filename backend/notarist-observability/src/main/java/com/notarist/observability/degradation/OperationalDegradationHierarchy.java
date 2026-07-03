package com.notarist.observability.degradation;

import com.notarist.observability.circuit.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives the system-level degradation tier from individual integration states.
 *
 * Hierarchy (highest severity wins):
 *   CRITICAL          — Oracle or PostgreSQL down; no data access; all operations blocked
 *   EMERGENCY         — MinIO down; no document storage; ingestion blocked
 *   SEARCH_ONLY       — Ollama down; assistant disabled; RAG search still returns results
 *   LIMITED_SEARCH    — Qdrant down; vector search unavailable; BM25 keyword only
 *   DEGRADED          — reranker OR embedding down; reduced quality but functional
 *   FULL              — all integrations healthy
 *
 * Callers (response strategy, audit, health endpoint) read getActiveLevel() rather than
 * checking each circuit breaker individually — single point of truth for degradation.
 */
@Component
public class OperationalDegradationHierarchy {

    private static final Logger log = LoggerFactory.getLogger(OperationalDegradationHierarchy.class);

    public enum DegradationLevel {
        FULL(0, "All systems operational"),
        DEGRADED(1, "Reduced quality — reranker or embedding impaired"),
        LIMITED_SEARCH(2, "Keyword-only search — Qdrant unavailable"),
        SEARCH_ONLY(3, "Search available — LLM assistant disabled"),
        EMERGENCY(4, "Ingestion blocked — document storage unavailable"),
        CRITICAL(5, "Data access lost — database unavailable");

        public final int    severity;
        public final String description;

        DegradationLevel(int severity, String description) {
            this.severity    = severity;
            this.description = description;
        }

        public boolean isAtLeast(DegradationLevel other) {
            return this.severity >= other.severity;
        }
    }

    public record DegradationSnapshot(
            DegradationLevel level,
            Instant          detectedAt,
            String           primaryCause
    ) {}

    private final CircuitBreakerRegistry              circuitBreakers;
    private final AtomicReference<DegradationSnapshot> currentSnapshot;
    private final MeterRegistry                       meterRegistry;

    public OperationalDegradationHierarchy(CircuitBreakerRegistry circuitBreakers,
                                            MeterRegistry meterRegistry) {
        this.circuitBreakers  = circuitBreakers;
        this.meterRegistry    = meterRegistry;
        this.currentSnapshot  = new AtomicReference<>(
                new DegradationSnapshot(DegradationLevel.FULL, Instant.now(), "startup"));

        Gauge.builder("notarist.ops.degradation.severity", currentSnapshot,
                        s -> s.get().level().severity)
                .description("Current degradation severity (0=FULL … 5=CRITICAL)")
                .register(meterRegistry);
    }

    /**
     * Recomputes degradation level from current circuit breaker states.
     * Call this after any circuit-breaker state change.
     */
    public DegradationLevel evaluate() {
        var states = circuitBreakers.snapshotStates();

        DegradationLevel level;
        String cause;

        if (isOpen(states, CircuitBreakerRegistry.Integration.POSTGRES)) {
            level = DegradationLevel.CRITICAL;
            cause = "PostgreSQL circuit OPEN";
        } else if (isOpen(states, CircuitBreakerRegistry.Integration.MINIO)) {
            level = DegradationLevel.EMERGENCY;
            cause = "MinIO circuit OPEN";
        } else if (isOpen(states, CircuitBreakerRegistry.Integration.OLLAMA)) {
            level = DegradationLevel.SEARCH_ONLY;
            cause = "Ollama circuit OPEN";
        } else if (isOpen(states, CircuitBreakerRegistry.Integration.QDRANT)) {
            level = DegradationLevel.LIMITED_SEARCH;
            cause = "Qdrant circuit OPEN";
        } else if (isOpen(states, CircuitBreakerRegistry.Integration.OCR)) {
            level = DegradationLevel.DEGRADED;
            cause = "OCR circuit OPEN";
        } else {
            level = DegradationLevel.FULL;
            cause = "all circuits CLOSED";
        }

        DegradationSnapshot previous = currentSnapshot.get();
        DegradationSnapshot next     = new DegradationSnapshot(level, Instant.now(), cause);
        currentSnapshot.set(next);

        if (previous.level() != level) {
            log.warn("OperationalDegradationHierarchy: {} → {} cause={}",
                    previous.level(), level, cause);
        }

        return level;
    }

    public DegradationLevel getActiveLevel() {
        return currentSnapshot.get().level();
    }

    public DegradationSnapshot getSnapshot() {
        return currentSnapshot.get();
    }

    private boolean isOpen(java.util.Map<CircuitBreakerRegistry.Integration, CircuitBreakerRegistry.State> states,
                            CircuitBreakerRegistry.Integration integration) {
        return states.get(integration) == CircuitBreakerRegistry.State.OPEN;
    }
}
