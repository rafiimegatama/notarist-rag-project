package com.notarist.observability.dashboard;

import com.notarist.observability.circuit.CircuitBreakerRegistry;
import com.notarist.observability.degradation.OperationalDegradationHierarchy;
import com.notarist.observability.health.HealthAggregationService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs for the operational dashboard view.
 *
 * All dashboard data is read-only. Writes go through OperationalCliFacade.
 * Assembled by OperationalHealthEndpoint from aggregated health + metrics snapshots.
 *
 * Design: flat, serialization-friendly records — Jackson can serialize these directly.
 * No domain types exposed; everything is converted to primitive-safe representations.
 */
public final class OperationalDashboardDto {

    private OperationalDashboardDto() {}

    public record SystemStatusDto(
            String  status,
            String  degradationLevel,
            String  degradationDescription,
            Instant evaluatedAt
    ) {
        public static SystemStatusDto from(HealthAggregationService.SystemHealth health) {
            return new SystemStatusDto(
                    health.status().name(),
                    health.degradationLevel().name(),
                    health.degradationLevel().description,
                    health.evaluatedAt()
            );
        }
    }

    public record CircuitBreakerDto(
            String integration,
            String state,
            boolean healthy
    ) {
        public static CircuitBreakerDto from(CircuitBreakerRegistry.Integration integration,
                                              CircuitBreakerRegistry.State state) {
            return new CircuitBreakerDto(
                    integration.name(),
                    state.name(),
                    state == CircuitBreakerRegistry.State.CLOSED
            );
        }
    }

    public record SubsystemDto(
            String  name,
            boolean healthy,
            String  detail
    ) {
        public static SubsystemDto from(HealthAggregationService.SubsystemHealth h) {
            return new SubsystemDto(h.name(), h.healthy(), h.detail());
        }
    }

    public record QueueDepthDto(
            String queue,
            int    depth,
            int    saturatedThreshold,
            boolean saturated
    ) {}

    public record SnapshotStatusDto(
            boolean ready,
            boolean stale,
            boolean hasStuckPipelines,
            int     dlqDepth,
            String  lastCompletedAt,
            String  diagnosis
    ) {}

    public record MetricsSummaryDto(
            long   avgIngestionLagMs,
            long   avgInferenceLatencyMs,
            long   avgEmbeddingLagMs,
            long   timeoutTotal,
            long   cancellationTotal,
            double degradationRate
    ) {}

    /**
     * Top-level dashboard envelope returned by the health endpoint.
     */
    public record DashboardSnapshot(
            SystemStatusDto          systemStatus,
            List<CircuitBreakerDto>  circuitBreakers,
            List<SubsystemDto>       subsystems,
            SnapshotStatusDto        snapshotStatus,
            MetricsSummaryDto        metrics,
            List<String>             activeWarnings,
            Instant                  generatedAt
    ) {
        public static DashboardSnapshot from(
                HealthAggregationService.SystemHealth health,
                SnapshotStatusDto snapshot,
                MetricsSummaryDto metrics,
                List<String> warnings) {

            List<CircuitBreakerDto> circuits = health.circuitStates().entrySet().stream()
                    .map(e -> CircuitBreakerDto.from(e.getKey(), e.getValue()))
                    .toList();

            List<SubsystemDto> subsystems = health.subsystems().values().stream()
                    .map(SubsystemDto::from)
                    .toList();

            return new DashboardSnapshot(
                    SystemStatusDto.from(health),
                    circuits,
                    subsystems,
                    snapshot,
                    metrics,
                    warnings,
                    Instant.now()
            );
        }
    }
}
