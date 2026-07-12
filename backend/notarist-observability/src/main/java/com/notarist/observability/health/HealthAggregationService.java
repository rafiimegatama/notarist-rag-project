package com.notarist.observability.health;

import com.notarist.infra.resilience.DegradedModeRegistry;
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
 *   - Real service degradation states, per external integration (DegradedModeRegistry —
 *     updated by the actual Qdrant/MinIO/Ollama/OCR adapters on every call)
 *   - Operational degradation level
 *   - Snapshot readiness (ingestion lag, DLQ depth, stuck pipelines)
 *
 * SystemHealthStatus derivation:
 *   UP       — FULL degradation, no service degraded, snapshot ready
 *   DEGRADED — non-FULL degradation or any service degraded
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
            Map<DegradedModeRegistry.ExternalService, DegradedModeRegistry.DegradedState> circuitStates,
            Map<String, SubsystemHealth>            subsystems,
            boolean                                 snapshotReady,
            Instant                                 evaluatedAt
    ) {}

    private final DegradedModeRegistry               degradedMode;
    private final OperationalDegradationHierarchy    degradationHierarchy;
    private final SnapshotReadinessChecker           snapshotChecker;

    public HealthAggregationService(
            DegradedModeRegistry degradedMode,
            OperationalDegradationHierarchy degradationHierarchy,
            SnapshotReadinessChecker snapshotChecker) {
        this.degradedMode          = degradedMode;
        this.degradationHierarchy  = degradationHierarchy;
        this.snapshotChecker       = snapshotChecker;
    }

    public SystemHealth aggregate() {
        OperationalDegradationHierarchy.DegradationLevel level = degradationHierarchy.evaluate();
        Map<DegradedModeRegistry.ExternalService, DegradedModeRegistry.DegradedState> services =
                degradedMode.snapshotStates();

        SnapshotReadinessChecker.SnapshotReadiness snapshot = snapshotChecker.check();

        Map<String, SubsystemHealth> subsystems = buildSubsystemMap(services, snapshot);
        SystemHealthStatus status = deriveStatus(level, services);

        if (status != SystemHealthStatus.UP) {
            log.warn("HealthAggregationService: status={} degradation={}", status, level);
        }

        return new SystemHealth(status, level, services, subsystems, snapshot.ready(), Instant.now());
    }

    private Map<String, SubsystemHealth> buildSubsystemMap(
            Map<DegradedModeRegistry.ExternalService, DegradedModeRegistry.DegradedState> services,
            SnapshotReadinessChecker.SnapshotReadiness snapshot) {

        Map<String, SubsystemHealth> map = new java.util.LinkedHashMap<>();

        for (DegradedModeRegistry.ExternalService service : DegradedModeRegistry.ExternalService.values()) {
            DegradedModeRegistry.DegradedState state = services.getOrDefault(
                    service, DegradedModeRegistry.DegradedState.healthy());
            map.put(service.name().toLowerCase(), new SubsystemHealth(
                    service.name(),
                    !state.degraded(),
                    state.degraded() ? "degraded reason=" + state.reason() : "healthy"
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
            Map<DegradedModeRegistry.ExternalService, DegradedModeRegistry.DegradedState> services) {

        if (level.isAtLeast(OperationalDegradationHierarchy.DegradationLevel.EMERGENCY)) {
            return SystemHealthStatus.DOWN;
        }

        boolean anyDegraded = services.values().stream()
                .anyMatch(DegradedModeRegistry.DegradedState::degraded);

        if (level != OperationalDegradationHierarchy.DegradationLevel.FULL || anyDegraded) {
            return SystemHealthStatus.DEGRADED;
        }

        return SystemHealthStatus.UP;
    }
}
