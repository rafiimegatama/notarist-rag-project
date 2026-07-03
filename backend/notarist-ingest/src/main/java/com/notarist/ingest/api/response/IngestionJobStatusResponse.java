package com.notarist.ingest.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IngestionJobStatusResponse(
    UUID jobId,
    UUID documentId,
    String currentStage,
    String status,
    Progress progress,
    List<StageRecord> stageHistory,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public record Progress(int completedStages, int totalStages, int percentComplete) {}

    public record StageRecord(
        String stage,
        String status,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        int attemptCount
    ) {}
}
