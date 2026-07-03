package com.notarist.assistant.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable trace envelope attached to every assistant response.
 * Required for audit, reproducibility, and debugging.
 *
 * Fields:
 *   traceId            — unique per response
 *   queryId            — from the incoming AssistantCommand
 *   sessionId          — conversation session
 *   promptVersion      — PromptVersion.version used for this response
 *   retrievalSnapshotId — UUID assigned at retrieval time; links to search audit log
 *   timestamp          — when orchestration started
 *   processingMs       — end-to-end latency; 0 until finalized
 */
public record ResponseTrace(
        UUID traceId,
        UUID queryId,
        UUID sessionId,
        String promptVersion,
        UUID retrievalSnapshotId,
        Instant timestamp,
        long processingMs
) {
    public static ResponseTrace create(UUID queryId, UUID sessionId,
                                       String promptVersion, UUID retrievalSnapshotId) {
        return new ResponseTrace(
                UUID.randomUUID(),
                queryId,
                sessionId,
                promptVersion,
                retrievalSnapshotId,
                Instant.now(),
                0L);
    }

    public ResponseTrace withProcessingMs(long ms) {
        return new ResponseTrace(traceId, queryId, sessionId, promptVersion,
                retrievalSnapshotId, timestamp, ms);
    }
}
