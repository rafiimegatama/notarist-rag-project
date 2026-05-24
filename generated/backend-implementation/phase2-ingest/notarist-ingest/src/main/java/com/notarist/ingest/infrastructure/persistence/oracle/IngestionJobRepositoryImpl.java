package com.notarist.ingest.infrastructure.persistence.oracle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.notarist.core.domain.valueobject.*;
import com.notarist.ingest.application.port.out.IngestJobRepository;
import com.notarist.ingest.domain.model.*;
import com.notarist.ingest.infrastructure.security.VpdContextApplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

/**
 * PHASE 6A.2-FIX:
 *   - toEntity(): removed .name() for enum fields (JPA handles via @Enumerated)
 *   - updateEntity(): removed .name() for enum setters
 *   - toDomain(): removed .valueOf() for enum fields (JPA delivers typed enum directly)
 *   - findByStatusAndTenantId(): passes PipelineStatus enum directly (not .name())
 *   - findFailedAndReadyForRetry(): passes PipelineStatus.FAILED enum (not hardcoded string)
 */
@Repository
public class IngestionJobRepositoryImpl implements IngestJobRepository {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final IngestionJobJpaRepository jpaRepository;
    private final VpdContextApplier vpdContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public IngestionJobRepositoryImpl(
            IngestionJobJpaRepository jpaRepository,
            VpdContextApplier vpdContextApplier) {
        this.jpaRepository = jpaRepository;
        this.vpdContextApplier = vpdContextApplier;
    }

    @Override
    public void save(IngestionJob job) {
        jpaRepository.findById(job.getIngestionId().value().toString())
                .map(existing -> updateEntity(existing, job))
                .ifPresentOrElse(
                        jpaRepository::save,
                        () -> jpaRepository.save(toEntity(job)));
    }

    @Override
    public Optional<IngestionJob> findByIngestionId(IngestionId ingestionId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(ingestionId.value().toString()).map(this::toDomain);
    }

    @Override
    public Optional<IngestionJob> findByJobId(JobId jobId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByJobId(jobId.value().toString()).map(this::toDomain);
    }

    @Override
    public Optional<IngestionJob> findByChecksumAndTenantId(String checksumSha256, UUID tenantId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByChecksumSha256AndTenantId(checksumSha256, tenantId.toString())
                .map(this::toDomain);
    }

    @Override
    public List<IngestionJob> findByStatusAndTenantId(PipelineStatus status, UUID tenantId, int limit) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByPipelineStatusAndTenantId(
                        status, tenantId.toString(), PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<IngestionJob> findFailedAndReadyForRetry(int maxRetries, int limit) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findFailedReadyForRetry(
                        PipelineStatus.FAILED, Instant.now(), maxRetries, PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    private IngestionJobJpaEntity toEntity(IngestionJob job) {
        return new IngestionJobJpaEntity(
                job.getIngestionId().value().toString(),
                job.getJobId().value().toString(),
                job.getDocumentId().value().toString(),
                job.getTenantId().toString(),
                job.getUploadedBy().toString(),
                job.getDocumentType(),
                job.getClassificationLevel(),
                job.getOriginalFilename(),
                job.getChecksum().sha256Hex(),
                job.getPipelineStatus(),
                job.getOverallStatus(),
                job.getFailureStage(),
                job.getRetryCount(),
                job.getLastErrorCode(),
                job.getLastErrorHash(),
                job.getNextRetryAt(),
                job.getDeadLetterReason(),
                serializeHistory(job.getStageHistory()),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }

    private IngestionJobJpaEntity updateEntity(IngestionJobJpaEntity entity, IngestionJob job) {
        entity.setPipelineStatus(job.getPipelineStatus());
        entity.setOverallStatus(job.getOverallStatus());
        entity.setFailureStage(job.getFailureStage());
        entity.setRetryCount(job.getRetryCount());
        entity.setLastErrorCode(job.getLastErrorCode());
        entity.setLastErrorHash(job.getLastErrorHash());
        entity.setNextRetryAt(job.getNextRetryAt());
        entity.setDeadLetterReason(job.getDeadLetterReason());
        entity.setStageHistoryJson(serializeHistory(job.getStageHistory()));
        entity.setUpdatedAt(job.getUpdatedAt());
        entity.setCompletedAt(job.getCompletedAt());
        return entity;
    }

    private IngestionJob toDomain(IngestionJobJpaEntity entity) {
        return IngestionJob.reconstruct(
                IngestionId.of(UUID.fromString(entity.getIngestionId())),
                new JobId(UUID.fromString(entity.getJobId())),
                new DocumentId(UUID.fromString(entity.getDocumentId())),
                UUID.fromString(entity.getTenantId()),
                UUID.fromString(entity.getUploadedBy()),
                new DocumentChecksum(entity.getChecksumSha256()),
                entity.getDocumentType(),
                entity.getClassificationLevel(),
                entity.getOriginalFilename(),
                entity.getPipelineStatus(),
                entity.getOverallStatus(),
                deserializeHistory(entity.getStageHistoryJson()),
                entity.getFailureStage(),
                entity.getRetryCount(),
                entity.getLastErrorCode(),
                entity.getLastErrorHash(),
                entity.getNextRetryAt(),
                entity.getDeadLetterReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCompletedAt());
    }

    private String serializeHistory(List<IngestionJob.StageRecord> records) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (IngestionJob.StageRecord r : records) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("stage", r.stage().name());
                entry.put("completedAt", r.completedAt().toString());
                entry.put("durationMs", r.durationMs());
                entry.put("errorCode", r.errorCode() != null ? r.errorCode() : "");
                entry.put("attemptNumber", r.attemptNumber());
                list.add(entry);
            }
            return JSON.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<IngestionJob.StageRecord> deserializeHistory(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return new ArrayList<>();
        try {
            List<Map<String, Object>> raw = JSON.readValue(json, new TypeReference<>() {});
            List<IngestionJob.StageRecord> records = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                String errorCode = (String) m.get("errorCode");
                records.add(new IngestionJob.StageRecord(
                        PipelineStatus.valueOf((String) m.get("stage")),
                        Instant.parse((String) m.get("completedAt")),
                        ((Number) m.get("durationMs")).longValue(),
                        (errorCode != null && !errorCode.isBlank()) ? errorCode : null,
                        ((Number) m.get("attemptNumber")).intValue()));
            }
            return records;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
