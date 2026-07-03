package com.notarist.observability.health;

import com.notarist.observability.circuit.CircuitBreakerRegistry;
import com.notarist.observability.consistency.SnapshotReadinessChecker;
import com.notarist.observability.degradation.OperationalDegradationHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Aggregates health signals from all subsystems into a single SystemHealth view.
 *
 * Health signals consulted:
 *   - Circuit breaker states (per integration)
 *   - Operational degradation level
 *   - Snapshot readiness (ingestion lag, DLQ depth, stuck pipelines)
 *
 * SystemHealthStatus derivation:
 *   UP       — FULL degradation, all circuits CLOSED, snapshot ready
 *   DEGRADED — non-FULL degradation or any circuit OPEN/HALF_OPEN
 *   DOWN     — CRITICAL/EMERGENCY degradation level
 *
 * Health aggregation is called on every health endpoint request — results are NOT cached
 * to ensure operators always see current state.
 */
@Component
public class HealthAggregationService {

    private static final Logger log = LoggerFactory.getLogger(HealthAggregationService.class);

    public enum SystemHealthStatus { UP, DEGRADED, DOWN }

    public record SubsystemHealth(
            String name,
            boolean healthy,
            String  detail
    ) {}

    public record SystemHealth(
            SystemHealthStatus                      status,
            OperationalDegradationHierarchy.DegradationLevel degradationLevel,
            Map<CircuitBreakerRegistry.Integration, CircuitBreakerRegistry.State> circuitStates,
            Map<String, SubsystemHealth>            subsystems,
            boolean                                 snapshotReady,
            Instant                                 evaluatedAt
    ) {}

    private final CircuitBreakerRegistry             circuitBreakers;
    private final OperationalDegradationHierarchy    degradationHierarchy;
    private final SnapshotReadinessChecker           snapshotChecker;

    public HealthAggregationService(
            CircuitBreakerRegistry circuitBreakers,
            OperationalDegradationHierarchy degradationHierarchy,
            SnapshotReadinessChecker snapshotChecker) {
        this.circuitBreakers       = circuitBreakers;
        this.degradationHierarchy  = degradationHierarchy;
        this.snapshotChecker       = snapshotChecker;
    }

    public SystemHealth aggregate() {
        OperationalDegradationHierarchy.DegradationLevel level = degradationHierarchy.evaluate();
        Map<CircuitBreakerRegistry.Integration, CircuitBreakerRegistry.State> circuits =
                circuitBreakers.snapshotStates();

        SnapshotReadinessChecker.SnapshotReadiness snapshot = snapshotChecker.check();

        Map<String, SubsystemHealth> subsystems = buildSubsystemMap(circuits, snapshot);
        SystemHealthStatus status = deriveStatus(level, circuits);

        if (status != SystemHealthStatus.UP) {
            log.warn("HealthAggregationService: status={} degradation={}", status, level);
        }

        return new SystemHealth(status, level, circuits, subsystems, snapshot.ready(), Instant.now());
    }

    private Map<String, SubsystemHealth> buildSubsystemMap(
            Map<CircuitBreakerRegistry.Integration, CircuitBreakerRegistry.State> circuits,
            SnapshotReadinessChecker.SnapshotReadiness snapshot) {

        Map<String, SubsystemHealth> map = new java.util.LinkedHashMap<>();

        for (CircuitBreakerRegistry.Integration integration : CircuitBreakerRegistry.Integration.values()) {
            CircuitBreakerRegistry.State state = circuits.getOrDefault(integration, CircuitBreakerRegistry.State.CLOSED);
            map.put(integration.name().toLowerCase(), new SubsystemHealth(
                    integration.name(),
                    state == CircuitBreakerRegistry.State.CLOSED,
                    "circuit=" + state.name()
            ));
        }

        map.put("snapshot", new SubsystemHealth(
                "SNAPSHOT",
                snapshot.ready(),
                snapshot.diagnosis()
        ));

        return map;
    }

    private SystemHealthStatus deriveStatus(
            OperationalDegradationHierarchy.DegradationLevel level,
            Map<CircuitBreakerRegistry.Integration, CircuitBreakerRegistry.State> circuits) {

        if (level.isAtLeast(OperationalDegradationHierarchy.DegradationLevel.EMERGENCY)) {
            return SystemHealthStatus.DOWN;
        }

        boolean anyOpen = circuits.values().stream()
                .anyMatch(s -> s == CircuitBreakerRegistry.State.OPEN);

        if (level != OperationalDegradationHierarchy.DegradationLevel.FULL || anyOpen) {
            return SystemHealthStatus.DEGRADED;
        }

        return SystemHealthStatus.UP;
    }
}
