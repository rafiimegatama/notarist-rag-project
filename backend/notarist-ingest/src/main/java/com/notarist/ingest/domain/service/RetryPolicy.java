package com.notarist.ingest.domain.service;

import java.time.Instant;

/** Domain service for exponential backoff retry scheduling. Zero Spring dependency. */
public final class RetryPolicy {

    private static final long BASE_DELAY_SECONDS = 30L;
    private static final long MAX_DELAY_SECONDS  = 3600L;  // 1 hour cap
    private static final int  MAX_SHIFT          = 6;      // caps growth at base * 2^6

    private RetryPolicy() {}

    /**
     * Computes next retry time using exponential backoff: {@code base * 2^(attempt-1)}.
     * attempt=1 → 30s, attempt=2 → 60s, attempt=3 → 120s, …, capped at 3600s.
     *
     * <p>attemptCount is 1-based: it is the number of the retry about to be scheduled, so the
     * first retry of a job (PipelineCoordinator passes {@code job.getRetryCount() + 1}, i.e. 1)
     * waits one BASE_DELAY. Retry COUNT semantics live in {@link #shouldRetry}, not here.
     */
    public static Instant computeNextRetryAt(int attemptCount) {
        return computeNextRetryAt(attemptCount, BASE_DELAY_SECONDS);
    }

    /** Same schedule as {@link #computeNextRetryAt(int)} with a caller-supplied base delay. */
    public static Instant computeNextRetryAt(int attemptCount, long baseDelaySeconds) {
        long delaySeconds = baseDelaySeconds * (1L << shiftFor(attemptCount));
        delaySeconds = Math.min(delaySeconds, MAX_DELAY_SECONDS);
        return Instant.now().plusSeconds(delaySeconds);
    }

    /** attempt=1 → shift 0 (one base delay). Clamped low and high: no negative shift, no overflow. */
    private static int shiftFor(int attemptCount) {
        if (attemptCount <= 1) return 0;
        return Math.min(attemptCount - 1, MAX_SHIFT);
    }

    /**
     * True while the job still has retries left.
     * retryCount is the number of retries ALREADY performed (0 for a job that has never
     * been retried) — unchanged by the backoff schedule above.
     */
    public static boolean shouldRetry(int retryCount, int maxRetries) {
        return retryCount < maxRetries;
    }

    /** Returns true when the job is past its next retry time. */
    public static boolean isReadyForRetry(Instant nextRetryAt) {
        return nextRetryAt == null || Instant.now().isAfter(nextRetryAt);
    }
}
