package com.notarist.observability.health;

import com.notarist.observability.consistency.SnapshotReadinessChecker;
import com.notarist.observability.dashboard.OperationalDashboardDto;
import com.notarist.observability.ops.OperationalCliFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Operational health REST endpoint.
 *
 * GET  /ops/health               — full dashboard snapshot (system status + circuits + metrics)
 * GET  /ops/health/live          — liveness probe: 200 if JVM alive
 * GET  /ops/health/ready         — readiness probe: 200 if not CRITICAL/EMERGENCY
 *
 * POST /ops/replay/queue         — operator: replay stuck queue items
 * POST /ops/replay/dlq           — operator: replay DLQ items by failure stage
 * POST /ops/reindex              — operator: trigger tenant reindex
 * GET  /ops/consistency/vectors  — operator: check vector consistency
 * GET  /ops/consistency/migrations — operator: validate migration state
 *
 * All POST endpoints require X-Operator-Id header (logged for audit).
 * No authentication enforcement here — caller's API gateway handles authz.
 *
 * Metrics: every call to /ops/health increments notarist.ops.health.check.total counter.
 */
@RestController
@RequestMapping("/ops")
public class OperationalHealthEndpoint {

    private static final Logger log = LoggerFactory.getLogger(OperationalHealthEndpoint.class);

    private final HealthAggregationService   healthService;
    private final SnapshotReadinessChecker   snapshotChecker;
    private final OperationalCliFacade       cliFacade;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public OperationalHealthEndpoint(
            HealthAggregationService healthService,
            SnapshotReadinessChecker snapshotChecker,
            OperationalCliFacade cliFacade,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.healthService  = healthService;
        this.snapshotChecker = snapshotChecker;
        this.cliFacade      = cliFacade;
        this.meterRegistry  = meterRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<OperationalDashboardDto.DashboardSnapshot> dashboard() {
        meterRegistry.counter("notarist.ops.health.check.total").increment();
        HealthAggregationService.SystemHealth health = healthService.aggregate();

        SnapshotReadinessChecker.SnapshotReadiness snapshot = snapshotChecker.check();
        OperationalDashboardDto.SnapshotStatusDto snapshotDto = new OperationalDashboardDto.SnapshotStatusDto(
                snapshot.ready(), snapshot.stale(), snapshot.hasStuckPipelines(),
                snapshot.dlqDepth(),
                snapshot.lastCompletedAt() != null ? snapshot.lastCompletedAt().toString() : null,
                snapshot.diagnosis()
        );

        OperationalDashboardDto.MetricsSummaryDto metrics =
                new OperationalDashboardDto.MetricsSummaryDto(0L, 0L, 0L, 0L, 0L, 0.0);

        List<String> warnings = buildWarnings(health);

        OperationalDashboardDto.DashboardSnapshot dto =
                OperationalDashboardDto.DashboardSnapshot.from(health, snapshotDto, metrics, warnings);

        int httpStatus = switch (health.status()) {
            case UP       -> 200;
            case DEGRADED -> 200;
            case DOWN     -> 503;
        };

        return ResponseEntity.status(httpStatus).body(dto);
    }

    @GetMapping("/health/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<String> readiness() {
        HealthAggregationService.SystemHealth health = healthService.aggregate();
        if (health.status() == HealthAggregationService.SystemHealthStatus.DOWN) {
            return ResponseEntity.status(503).body("NOT_READY: " + health.degradationLevel().name());
        }
        return ResponseEntity.ok("READY");
    }

    @PostMapping("/replay/queue")
    public ResponseEntity<OperationalCliFacade.OperationResult> replayQueue(
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "anonymous") String operatorId) {
        OperationalCliFacade.OperationResult result = cliFacade.replayQueue(tenantId, operatorId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/replay/dlq")
    public ResponseEntity<OperationalCliFacade.OperationResult> replayDlq(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String failureStage,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "anonymous") String operatorId) {
        OperationalCliFacade.OperationResult result = cliFacade.replayDlq(tenantId, failureStage, operatorId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reindex")
    public ResponseEntity<OperationalCliFacade.OperationResult> triggerReindex(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "operator-triggered") String reason,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "anonymous") String operatorId) {
        OperationalCliFacade.OperationResult result = cliFacade.triggerReindex(tenantId, operatorId, reason);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/consistency/vectors")
    public ResponseEntity<OperationalCliFacade.OperationResult> checkVectors(
            @RequestParam String tenantId,
            @RequestHeader(value = "X-Operator-Id", defaultValue = "anonymous") String operatorId) {
        return ResponseEntity.ok(cliFacade.checkVectorConsistency(tenantId, operatorId));
    }

    @GetMapping("/consistency/migrations")
    public ResponseEntity<OperationalCliFacade.OperationResult> checkMigrations(
            @RequestHeader(value = "X-Operator-Id", defaultValue = "anonymous") String operatorId) {
        return ResponseEntity.ok(cliFacade.validateMigrations(operatorId));
    }

    private List<String> buildWarnings(HealthAggregationService.SystemHealth health) {
        List<String> warnings = new ArrayList<>();
        if (health.status() != HealthAggregationService.SystemHealthStatus.UP) {
            warnings.add("System is " + health.status().name() + ": " + health.degradationLevel().description);
        }
        health.circuitStates().forEach((service, state) -> {
            if (state.degraded()) {
                warnings.add("Service DEGRADED: " + service.name()
                        + (state.reason() != null ? " (" + state.reason() + ")" : ""));
            }
        });
        if (!health.snapshotReady()) {
            warnings.add("Ingestion snapshot is not ready — search results may be stale");
        }
        return warnings;
    }
}
