package com.notarist.observability.ops;

import com.notarist.observability.consistency.MigrationConsistencyValidator;
import com.notarist.observability.consistency.SnapshotReadinessChecker;
import com.notarist.observability.consistency.VectorConsistencyChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified facade for operational commands.
 *
 * Exposes a command-style API that can be called from:
 *   - REST endpoint (OperationalHealthEndpoint)
 *   - Internal scheduled jobs
 *   - Integration tests
 *
 * All commands are logged with operatorId and produce a structured OperationResult.
 * Commands are synchronous; long-running operations (reindex) return immediately
 * with a jobId for tracking.
 */
@Component
public class OperationalCliFacade {

    private static final Logger log = LoggerFactory.getLogger(OperationalCliFacade.class);

    public record OperationResult(
            String              command,
            String              status,
            Map<String, Object> payload,
            Instant             executedAt,
            String              operatorId
    ) {
        public static OperationResult success(String command, String operatorId, Map<String, Object> payload) {
            return new OperationResult(command, "SUCCESS", payload, Instant.now(), operatorId);
        }
        public static OperationResult failure(String command, String operatorId, String error) {
            return new OperationResult(command, "FAILURE",
                    Map.of("error", error), Instant.now(), operatorId);
        }
    }

    private final QueueReplayService             queueReplay;
    private final DlqReplayService               dlqReplay;
    private final ReindexTriggerService          reindexTrigger;
    private final VectorConsistencyChecker       vectorConsistency;
    private final SnapshotReadinessChecker       snapshotReadiness;
    private final MigrationConsistencyValidator  migrationValidator;

    public OperationalCliFacade(
            QueueReplayService queueReplay,
            DlqReplayService dlqReplay,
            ReindexTriggerService reindexTrigger,
            VectorConsistencyChecker vectorConsistency,
            SnapshotReadinessChecker snapshotReadiness,
            MigrationConsistencyValidator migrationValidator) {
        this.queueReplay        = queueReplay;
        this.dlqReplay          = dlqReplay;
        this.reindexTrigger     = reindexTrigger;
        this.vectorConsistency  = vectorConsistency;
        this.snapshotReadiness  = snapshotReadiness;
        this.migrationValidator = migrationValidator;
    }

    public OperationResult replayQueue(String tenantId, String operatorId) {
        log.info("OperationalCli: REPLAY_QUEUE tenantId={} operatorId={}", tenantId, operatorId);
        try {
            QueueReplayService.ReplayResult result = queueReplay.replayStuckPipelineRuns(tenantId, operatorId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("replayedCount", result.replayedCount());
            payload.put("replayedIds", result.replayedIds());
            payload.put("replayStatus", result.status());
            return OperationResult.success("REPLAY_QUEUE", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("REPLAY_QUEUE", operatorId, e.getMessage());
        }
    }

    public OperationResult replayDlq(String tenantId, String failureStage, String operatorId) {
        log.info("OperationalCli: REPLAY_DLQ tenantId={} stage={} operatorId={}", tenantId, failureStage, operatorId);
        try {
            DlqReplayService.DlqReplayResult result = dlqReplay.replayByFailureStage(
                    failureStage, tenantId, DlqReplayService.ReplayMode.FORCE_RETRY, operatorId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("processedCount", result.processedCount());
            payload.put("mode", result.mode() != null ? result.mode().name() : null);
            payload.put("status", result.status());
            return OperationResult.success("REPLAY_DLQ", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("REPLAY_DLQ", operatorId, e.getMessage());
        }
    }

    public OperationResult triggerReindex(String tenantId, String operatorId, String reason) {
        log.info("OperationalCli: TRIGGER_REINDEX tenantId={} operatorId={} reason={}", tenantId, operatorId, reason);
        try {
            ReindexTriggerService.ReindexResult result = reindexTrigger.trigger(
                    new ReindexTriggerService.ReindexRequest(
                            ReindexTriggerService.ReindexScope.TENANT,
                            tenantId, null, operatorId, reason));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reindexJobId", result.reindexJobId());
            payload.put("documentsQueued", result.documentsQueued());
            payload.put("status", result.status());
            return OperationResult.success("TRIGGER_REINDEX", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("TRIGGER_REINDEX", operatorId, e.getMessage());
        }
    }

    public OperationResult checkVectorConsistency(String tenantId, String operatorId) {
        log.info("OperationalCli: CHECK_VECTOR_CONSISTENCY tenantId={} operatorId={}", tenantId, operatorId);
        try {
            VectorConsistencyChecker.ConsistencyReport report = vectorConsistency.checkSample(tenantId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("consistent",         report.consistent());
            payload.put("postgresChunkCount", report.postgresChunkCount());
            payload.put("qdrantVectorCount",  report.qdrantVectorCount());
            payload.put("missingInQdrant",    report.missingInQdrant());
            payload.put("versionMismatches",  report.versionMismatches());
            payload.put("sampled",            report.sampled());
            payload.put("diagnosis",          report.diagnosis());
            return OperationResult.success("CHECK_VECTOR_CONSISTENCY", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("CHECK_VECTOR_CONSISTENCY", operatorId, e.getMessage());
        }
    }

    public OperationResult validateMigrations(String operatorId) {
        log.info("OperationalCli: VALIDATE_MIGRATIONS operatorId={}", operatorId);
        try {
            MigrationConsistencyValidator.MigrationReport report = migrationValidator.validate();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("valid",                  report.valid());
            payload.put("hasCriticalViolations",  report.hasCriticalViolations());
            payload.put("violationCount",         report.violations().size());
            payload.put("violations",             report.violations());
            return OperationResult.success("VALIDATE_MIGRATIONS", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("VALIDATE_MIGRATIONS", operatorId, e.getMessage());
        }
    }

    public OperationResult checkSnapshotReadiness(String operatorId) {
        log.info("OperationalCli: CHECK_SNAPSHOT_READINESS operatorId={}", operatorId);
        try {
            SnapshotReadinessChecker.SnapshotReadiness readiness = snapshotReadiness.check();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ready",              readiness.ready());
            payload.put("stale",              readiness.stale());
            payload.put("hasStuckPipelines",  readiness.hasStuckPipelines());
            payload.put("dlqDepth",           readiness.dlqDepth());
            payload.put("lastCompletedAt",    readiness.lastCompletedAt() != null
                    ? readiness.lastCompletedAt().toString() : null);
            payload.put("diagnosis",          readiness.diagnosis());
            return OperationResult.success("CHECK_SNAPSHOT_READINESS", operatorId, payload);
        } catch (Exception e) {
            return OperationResult.failure("CHECK_SNAPSHOT_READINESS", operatorId, e.getMessage());
        }
    }
}
