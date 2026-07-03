package com.notarist.observability.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Triggers vector reindexing for a tenant or specific document.
 *
 * Reindex is needed when:
 *   - EmbeddingVersionManager.requiresReindex() returns true (model upgrade)
 *   - VectorConsistencyChecker finds missingInQdrant > 0
 *   - Operator explicitly requests rebuild after Qdrant collection reset
 *
 * Mechanism: inserts a REINDEX task into pipeline_run with a special run_type='REINDEX'.
 * The ingestion worker picks it up via the same PipelineStatus state machine.
 *
 * Scope controls:
 *   - TENANT scope: reindex all documents for a tenant
 *   - DOCUMENT scope: reindex one specific document
 *   - GLOBAL scope: admin-only; reindex everything (expensive)
 */
@Component
public class ReindexTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ReindexTriggerService.class);

    public enum ReindexScope { TENANT, DOCUMENT, GLOBAL }

    public record ReindexRequest(
            ReindexScope scope,
            String       tenantId,
            String       documentId,
            String       operatorId,
            String       reason
    ) {}

    public record ReindexResult(
            String  reindexJobId,
            int     documentsQueued,
            String  status,
            Instant scheduledAt
    ) {
        public static ReindexResult error(String m) {
            return new ReindexResult(null, 0, "ERROR: " + m, Instant.now());
        }
    }

    private final JdbcTemplate postgresJdbcTemplate;

    public ReindexTriggerService(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    public ReindexResult trigger(ReindexRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("ReindexTriggerService: trigger scope={} tenantId={} documentId={} " +
                        "operatorId={} reason={} jobId={}",
                request.scope(), request.tenantId(), request.documentId(),
                request.operatorId(), request.reason(), jobId);

        try {
            return switch (request.scope()) {
                case DOCUMENT -> triggerDocumentReindex(request, jobId);
                case TENANT   -> triggerTenantReindex(request, jobId);
                case GLOBAL   -> triggerGlobalReindex(request, jobId);
            };
        } catch (Exception e) {
            log.error("ReindexTriggerService: trigger failed jobId={}: {}", jobId, e.getMessage());
            return ReindexResult.error(e.getMessage());
        }
    }

    private ReindexResult triggerDocumentReindex(ReindexRequest request, String jobId) {
        postgresJdbcTemplate.update("""
                INSERT INTO pipeline_run (
                    run_id, document_id, tenant_id, run_type, status,
                    triggered_by, trigger_reason, created_at
                ) VALUES (?::uuid, ?::uuid, ?::uuid, 'REINDEX', 'PENDING', ?, ?, NOW())
                """,
                jobId, request.documentId(), request.tenantId(),
                request.operatorId(), request.reason());

        return new ReindexResult(jobId, 1, "QUEUED", Instant.now());
    }

    private ReindexResult triggerTenantReindex(ReindexRequest request, String jobId) {
        // Mark all existing embeddings for this tenant as requiring reindex
        int count = postgresJdbcTemplate.update("""
                UPDATE chunk_index
                SET embedding_version = 'REINDEX_REQUIRED',
                    reindex_job_id = ?::uuid
                WHERE tenant_id = ?::uuid
                  AND embedding_version != 'REINDEX_REQUIRED'
                """, jobId, request.tenantId());

        // Insert one coordinator run that the worker will expand into per-document jobs
        postgresJdbcTemplate.update("""
                INSERT INTO pipeline_run (
                    run_id, tenant_id, run_type, status,
                    triggered_by, trigger_reason, affected_chunk_count, created_at
                ) VALUES (?::uuid, ?::uuid, 'REINDEX_COORDINATOR', 'PENDING', ?, ?, ?, NOW())
                """,
                jobId, request.tenantId(),
                request.operatorId(), request.reason(), count);

        log.info("ReindexTriggerService: tenant reindex queued tenantId={} chunksMarked={} jobId={}",
                request.tenantId(), count, jobId);
        return new ReindexResult(jobId, count, "QUEUED", Instant.now());
    }

    private ReindexResult triggerGlobalReindex(ReindexRequest request, String jobId) {
        log.warn("ReindexTriggerService: GLOBAL reindex triggered by operatorId={} reason={}",
                request.operatorId(), request.reason());

        int count = postgresJdbcTemplate.update("""
                UPDATE chunk_index
                SET embedding_version = 'REINDEX_REQUIRED',
                    reindex_job_id = ?::uuid
                WHERE embedding_version != 'REINDEX_REQUIRED'
                """, jobId);

        postgresJdbcTemplate.update("""
                INSERT INTO pipeline_run (
                    run_id, run_type, status, triggered_by, trigger_reason, affected_chunk_count, created_at
                ) VALUES (?::uuid, 'REINDEX_GLOBAL', 'PENDING', ?, ?, ?, NOW())
                """,
                jobId, request.operatorId(), request.reason(), count);

        return new ReindexResult(jobId, count, "QUEUED", Instant.now());
    }
}
