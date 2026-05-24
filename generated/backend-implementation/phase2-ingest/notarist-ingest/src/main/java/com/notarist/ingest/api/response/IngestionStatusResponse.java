package com.notarist.ingest.api.response;

import com.notarist.ingest.domain.model.PipelineStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IngestionStatusResponse(
        UUID ingestionId,
        UUID jobId,
        UUID documentId,
        PipelineStatus pipelineStatus,
        String overallStatus,
        int retryCount,
        String lastErrorCode,
        String failureStage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<StageEntry> stageHistory
) {
    public record StageEntry(
            String stage,
            String completedAt,
            long durationMs,
            String errorCode,
            int attemptNumber
    ) {}
}
