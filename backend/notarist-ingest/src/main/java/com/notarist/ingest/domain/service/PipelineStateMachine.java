package com.notarist.ingest.domain.service;

import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.PipelineStatus;

import java.util.Optional;

/** Domain service enforcing valid PipelineStatus transitions. Zero Spring dependency. */
public final class PipelineStateMachine {

    private PipelineStateMachine() {}

    public static void assertValidTransition(PipelineStatus from, PipelineStatus to) {
        if (!isValidTransition(from, to)) {
            throw new IngestionStageException(
                    "INGEST_INVALID_TRANSITION", from, false,
                    "Invalid pipeline transition: " + from + " → " + to);
        }
    }

    public static boolean isValidTransition(PipelineStatus from, PipelineStatus to) {
        return switch (from) {
            case UPLOADED       -> to == PipelineStatus.OCR_PENDING;
            case OCR_PENDING    -> to == PipelineStatus.OCR_COMPLETED || to == PipelineStatus.FAILED;
            case OCR_COMPLETED  -> to == PipelineStatus.NER_PENDING;
            case NER_PENDING    -> to == PipelineStatus.NER_COMPLETED || to == PipelineStatus.FAILED;
            case NER_COMPLETED  -> to == PipelineStatus.CHUNK_PENDING;
            case CHUNK_PENDING  -> to == PipelineStatus.CHUNK_COMPLETED || to == PipelineStatus.FAILED;
            case CHUNK_COMPLETED -> to == PipelineStatus.EMBED_PENDING;
            case EMBED_PENDING  -> to == PipelineStatus.INDEX_PENDING || to == PipelineStatus.FAILED;
            case INDEX_PENDING  -> to == PipelineStatus.COMPLETED || to == PipelineStatus.FAILED;
            case FAILED         -> to == PipelineStatus.OCR_PENDING || to == PipelineStatus.NER_PENDING
                                   || to == PipelineStatus.CHUNK_PENDING || to == PipelineStatus.EMBED_PENDING
                                   || to == PipelineStatus.INDEX_PENDING || to == PipelineStatus.DLQ;
            default             -> false;
        };
    }

    /** Returns the next PENDING stage to enqueue after a COMPLETED stage. */
    public static Optional<PipelineStatus> nextPendingStage(PipelineStatus completedStage) {
        return switch (completedStage) {
            case UPLOADED       -> Optional.of(PipelineStatus.OCR_PENDING);
            case OCR_COMPLETED  -> Optional.of(PipelineStatus.NER_PENDING);
            case NER_COMPLETED  -> Optional.of(PipelineStatus.CHUNK_PENDING);
            case CHUNK_COMPLETED -> Optional.of(PipelineStatus.EMBED_PENDING);
            case EMBED_PENDING  -> Optional.of(PipelineStatus.INDEX_PENDING);
            default             -> Optional.empty();
        };
    }

    /** Returns the status a job should transition to once the given PENDING stage's processing succeeds. */
    public static PipelineStatus completedStageFor(PipelineStatus pendingStage) {
        return switch (pendingStage) {
            case OCR_PENDING    -> PipelineStatus.OCR_COMPLETED;
            case NER_PENDING    -> PipelineStatus.NER_COMPLETED;
            case CHUNK_PENDING  -> PipelineStatus.CHUNK_COMPLETED;
            case EMBED_PENDING  -> PipelineStatus.INDEX_PENDING;
            case INDEX_PENDING  -> PipelineStatus.COMPLETED;
            default -> throw new IllegalArgumentException("No completed stage for: " + pendingStage);
        };
    }

    /** Returns the retry stage (same stage) for a failed job. */
    public static PipelineStatus retryStageFor(String failureStage) {
        if (failureStage == null) return PipelineStatus.OCR_PENDING;
        try {
            PipelineStatus failed = PipelineStatus.valueOf(failureStage);
            return switch (failed) {
                case OCR_PENDING    -> PipelineStatus.OCR_PENDING;
                case NER_PENDING    -> PipelineStatus.NER_PENDING;
                case CHUNK_PENDING  -> PipelineStatus.CHUNK_PENDING;
                case EMBED_PENDING  -> PipelineStatus.EMBED_PENDING;
                case INDEX_PENDING  -> PipelineStatus.INDEX_PENDING;
                default             -> PipelineStatus.OCR_PENDING;
            };
        } catch (IllegalArgumentException e) {
            return PipelineStatus.OCR_PENDING;
        }
    }
}
