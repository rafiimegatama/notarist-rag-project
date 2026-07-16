package com.notarist.observability.health;

import com.notarist.core.security.VpdContextHolder;
import com.notarist.observability.consistency.SnapshotReadinessChecker;
import com.notarist.observability.dashboard.OperationalDashboardDto;
import com.notarist.observability.ops.OperationalCliFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Operational health + operator actions.
 *
 * GET  /ops/health               — full dashboard snapshot (ADMIN)
 * GET  /ops/health/live          — liveness probe: 200 if JVM alive (open — probers carry no JWT)
 * GET  /ops/health/ready         — readiness probe: 200 if not CRITICAL/EMERGENCY (open)
 *
 * POST /ops/replay/queue         — replay stuck queue items      (ADMIN, caller's tenant)
 * POST /ops/replay/dlq           — replay DLQ items              (ADMIN, caller's tenant)
 * POST /ops/reindex              — trigger reindex               (ADMIN, caller's tenant)
 * GET  /ops/consistency/vectors  — check vector consistency      (ADMIN, caller's tenant)
 * GET  /ops/consistency/migrations — validate migration state    (ADMIN)
 *
 * <h2>Security — this endpoint used to be a cross-tenant backdoor</h2>
 *
 * It previously carried the comment "No authentication enforcement here — caller's API gateway
 * handles authz." There is no such gateway: the service is exposed directly (Cloud Run, allUsers)
 * and its OWN JWT layer is the only boundary. The result was that ANY authenticated user — down to
 * the lowest {@code STAFF} role — could call these endpoints, and because {@code tenantId} arrived
 * as a {@code @RequestParam}, they could aim a destructive DLQ replay or a reindex at ANY tenant.
 * That defeated the PostgreSQL row-level-security tenant isolation the rest of the system enforces,
 * because the tenant never came from the token. {@code operatorId} was likewise a caller-supplied
 * header, so the audit trail of who did it was spoofable.
 *
 * Two rules now hold, and both must keep holding:
 *
 * <ol>
 *   <li><b>ADMIN only.</b> Asserted with {@code @PreAuthorize} on the class, so a new method added
 *       here is protected by default rather than by remembering to protect it. The URL matcher in
 *       {@code SecurityConfig} is a second, independent layer.</li>
 *   <li><b>The tenant and the operator come from the JWT, never from the request.</b> There is
 *       deliberately no {@code tenantId} parameter any more. An admin operates on their OWN tenant.
 *       A genuine platform-wide, cross-tenant operation is a different capability with a different
 *       threat model — it must not be reachable by typing a different value into a query string.</li>
 * </ol>
 *
 * Metrics: every call to /ops/health increments notarist.ops.health.check.total counter.
 */
@RestController
@RequestMapping("/ops")
@PreAuthorize("hasRole('ADMIN')")
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

    /**
     * Liveness/readiness are the two exceptions to the ADMIN rule: a Cloud Run / Kubernetes / docker
     * prober carries no JWT, and an authenticated health probe is one that reports DOWN the moment
     * auth breaks. They expose no tenant data — "OK" and "READY" — so they are safe to leave open.
     */
    @GetMapping("/health/live")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/health/ready")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> readiness() {
        HealthAggregationService.SystemHealth health = healthService.aggregate();
        if (health.status() == HealthAggregationService.SystemHealthStatus.DOWN) {
            return ResponseEntity.status(503).body("NOT_READY: " + health.degradationLevel().name());
        }
        return ResponseEntity.ok("READY");
    }

    @PostMapping("/replay/queue")
    public ResponseEntity<OperationalCliFacade.OperationResult> replayQueue() {
        Operator operator = currentOperator();
        log.info("ops: replayQueue tenantId={} operatorId={}", operator.tenantId(), operator.operatorId());
        return ResponseEntity.ok(cliFacade.replayQueue(operator.tenantId(), operator.operatorId()));
    }

    @PostMapping("/replay/dlq")
    public ResponseEntity<OperationalCliFacade.OperationResult> replayDlq(
            // failureStage narrows WHICH of the caller's own dead letters are replayed. It selects
            // within the tenant; it cannot cross out of it, so it stays a request parameter.
            @RequestParam(required = false) String failureStage) {
        Operator operator = currentOperator();
        log.info("ops: replayDlq tenantId={} failureStage={} operatorId={}",
                operator.tenantId(), failureStage, operator.operatorId());
        return ResponseEntity.ok(
                cliFacade.replayDlq(operator.tenantId(), failureStage, operator.operatorId()));
    }

    @PostMapping("/reindex")
    public ResponseEntity<OperationalCliFacade.OperationResult> triggerReindex(
            @RequestParam(defaultValue = "operator-triggered") String reason) {
        Operator operator = currentOperator();
        log.info("ops: triggerReindex tenantId={} reason={} operatorId={}",
                operator.tenantId(), reason, operator.operatorId());
        return ResponseEntity.accepted().body(
                cliFacade.triggerReindex(operator.tenantId(), operator.operatorId(), reason));
    }

    @GetMapping("/consistency/vectors")
    public ResponseEntity<OperationalCliFacade.OperationResult> checkVectors() {
        Operator operator = currentOperator();
        return ResponseEntity.ok(
                cliFacade.checkVectorConsistency(operator.tenantId(), operator.operatorId()));
    }

    @GetMapping("/consistency/migrations")
    public ResponseEntity<OperationalCliFacade.OperationResult> checkMigrations() {
        // Migration state is a property of the DATABASE, not of a tenant — no tenantId to pass.
        return ResponseEntity.ok(cliFacade.validateMigrations(currentOperator().operatorId()));
    }

    /** The authenticated caller, as the ONLY source of tenant and operator identity. */
    private record Operator(String tenantId, String operatorId) {}

    /**
     * Resolves the operator from the JWT.
     *
     * <p>This is the fix for the cross-tenant hole: the tenant is whatever the token says, so an
     * admin of tenant A cannot replay tenant B's dead letters or trigger tenant B's reindex, no
     * matter what they put in the request. The operator id is the authenticated user id, so the
     * audit record of who ran a destructive operation is no longer a caller-supplied string.
     *
     * <p>Throws rather than defaulting to "anonymous"/null. A destructive operation with no
     * identifiable tenant and no identifiable operator is exactly the operation that must not run.
     */
    private Operator currentOperator() {
        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get().orElseThrow(() ->
                new IllegalStateException(
                        "No authenticated principal on an /ops request. Operator actions require a "
                        + "JWT — tenant and operator identity are never taken from the request."));

        return new Operator(principal.tenantId().toString(), principal.userId().toString());
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
