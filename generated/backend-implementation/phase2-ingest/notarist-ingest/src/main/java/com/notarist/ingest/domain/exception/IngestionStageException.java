package com.notarist.ingest.domain.exception;

import com.notarist.ingest.domain.model.PipelineStatus;

/** Thrown when a pipeline stage fails — carries error code and retry eligibility. */
public class IngestionStageException extends RuntimeException {

    private final String errorCode;
    private final PipelineStatus failedAtStatus;
    private final boolean retryable;

    public IngestionStageException(
            String errorCode,
            PipelineStatus failedAtStatus,
            boolean retryable,
            String message) {
        super(message);
        this.errorCode = errorCode;
        this.failedAtStatus = failedAtStatus;
        this.retryable = retryable;
    }

    public IngestionStageException(
            String errorCode,
            PipelineStatus failedAtStatus,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.failedAtStatus = failedAtStatus;
        this.retryable = retryable;
    }

    public static IngestionStageException retryable(
            String errorCode, PipelineStatus stage, String message) {
        return new IngestionStageException(errorCode, stage, true, message);
    }

    public static IngestionStageException fatal(
            String errorCode, PipelineStatus stage, String message) {
        return new IngestionStageException(errorCode, stage, false, message);
    }

    public String getErrorCode() { return errorCode; }
    public PipelineStatus getFailedAtStatus() { return failedAtStatus; }
    public boolean isRetryable() { return retryable; }
}
