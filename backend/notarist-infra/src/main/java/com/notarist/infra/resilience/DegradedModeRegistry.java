package com.notarist.infra.resilience;

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
 * Thread-safe registry tracking which external services are currently degraded.
 *
 * Services self-report via markDegraded() on integration failure and markHealthy() on success.
 * Health indicators read from this registry to include degradation state in actuator output.
 * Micrometer Gauges expose degraded state (1=degraded, 0=healthy) per service.
 *
 * This is NOT a full circuit-breaker — it is a degradation tracking layer.
 * Resilience4j circuit-breaker integration can be layered on top in Phase 5C.
 */
@Component
public class DegradedModeRegistry {

    private static final Logger log = LoggerFactory.getLogger(DegradedModeRegistry.class);

    public enum ExternalService { GCS, QDRANT, POSTGRES, OLLAMA, OCR, EMBEDDING }

    private final Map<ExternalService, DegradedState> state = new ConcurrentHashMap<>();

    public DegradedModeRegistry(MeterRegistry meterRegistry) {
        for (ExternalService service : ExternalService.values()) {
            state.put(service, DegradedState.healthy());
            // Expose as Gauge: 1 = degraded, 0 = healthy
            String serviceName = service.name().toLowerCase();
            Gauge.builder("notarist.infra.service.degraded." + serviceName,
                            () -> state.get(service).degraded() ? 1.0 : 0.0)
                    .description("1 if service is degraded, 0 if healthy: " + serviceName)
                    .register(meterRegistry);
        }
    }

    public void markDegraded(ExternalService service, String reason) {
        DegradedState current = state.get(service);
        int failures = current.degraded() ? current.consecutiveFailures() + 1 : 1;
        state.put(service, new DegradedState(true, Instant.now(), reason, failures));
        log.warn("Service DEGRADED: {} (failures={}) reason={}", service, failures, reason);
    }

    public void markHealthy(ExternalService service) {
        DegradedState current = state.get(service);
        if (current.degraded()) {
            state.put(service, DegradedState.healthy());
            log.info("Service RECOVERED: {}", service);
        }
    }

    public boolean isDegraded(ExternalService service) {
        return state.get(service).degraded();
    }

    public DegradedState getState(ExternalService service) {
        return state.get(service);
    }

    /** Snapshot of current state for every tracked service — used by health/degradation reporting. */
    public Map<ExternalService, DegradedState> snapshotStates() {
        Map<ExternalService, DegradedState> snapshot = new EnumMap<>(ExternalService.class);
        snapshot.putAll(state);
        return snapshot;
    }

    public record DegradedState(
            boolean degraded,
            Instant degradedSince,
            String reason,
            int consecutiveFailures
    ) {
        public static DegradedState healthy() {
            return new DegradedState(false, null, null, 0);
        }
    }
}
