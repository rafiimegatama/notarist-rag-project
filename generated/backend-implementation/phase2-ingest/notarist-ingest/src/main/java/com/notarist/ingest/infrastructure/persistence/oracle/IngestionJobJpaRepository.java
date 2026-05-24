package com.notarist.ingest.infrastructure.persistence.oracle;

import com.notarist.ingest.domain.model.PipelineStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PHASE 6A.2-FIX:
 *   - findByPipelineStatusAndTenantId: param type changed from String to PipelineStatus
 *   - findFailedReadyForRetry: hardcoded 'FAILED' literal replaced with :failedStatus param
 */
public interface IngestionJobJpaRepository extends JpaRepository<IngestionJobJpaEntity, String> {

    Optional<IngestionJobJpaEntity> findByJobId(String jobId);

    Optional<IngestionJobJpaEntity> findByChecksumSha256AndTenantId(String checksumSha256, String tenantId);

    List<IngestionJobJpaEntity> findByPipelineStatusAndTenantId(
            PipelineStatus pipelineStatus, String tenantId, Pageable pageable);

    @Query("""
            SELECT j FROM IngestionJobJpaEntity j
            WHERE j.pipelineStatus = :failedStatus
              AND j.nextRetryAt IS NOT NULL
              AND j.nextRetryAt <= :now
              AND j.retryCount < :maxRetries
            ORDER BY j.nextRetryAt ASC
            """)
    List<IngestionJobJpaEntity> findFailedReadyForRetry(
            @Param("failedStatus") PipelineStatus failedStatus,
            @Param("now") Instant now,
            @Param("maxRetries") int maxRetries,
            Pageable pageable);
}
