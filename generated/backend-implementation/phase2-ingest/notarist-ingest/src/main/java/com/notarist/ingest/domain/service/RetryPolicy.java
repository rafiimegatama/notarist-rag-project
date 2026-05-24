package com.notarist.ingest.domain.service;

import java.time.Instant;

/** Domain service for exponential backoff retry scheduling. Zero Spring dependency. */
public final class RetryPolicy {

    private static final long BASE_DELAY_SECONDS = 30L;
    private static final long MAX_DELAY_SECONDS  = 3600L;  // 1 hour cap

    private RetryPolicy() {}

    /**
     * Computes next retry time using exponential backoff.
     * attempt=1 → 30s, attempt=2 → 60s, attempt=3 → 120s, …, capped at 3600s.
     */
    public static Instant computeNextRetryAt(int attemptCount) {
        long delaySeconds = BASE_DELAY_SECONDS * (1L << Math.min(attemptCount, 6));
        delaySeconds = Math.min(delaySeconds, MAX_DELAY_SECONDS);
        return Instant.now().plusSeconds(delaySeconds);
    }

    public static Instant computeNextRetryAt(int attemptCount, long baseDelaySeconds) {
        long delaySeconds = baseDelaySeconds * (1L << Math.min(attemptCount, 6));
        delaySeconds = Math.min(delaySeconds, MAX_DELAY_SECONDS);
        return Instant.now().plusSeconds(delaySeconds);
    }

    public static boolean shouldRetry(int retryCount, int maxRetries) {
        return retryCount < maxRetries;
    }

    /** Returns true when the job is past its next retry time. */
    public static boolean isReadyForRetry(Instant nextRetryAt) {
        return nextRetryAt == null || Instant.now().isAfter(nextRetryAt);
    }
}
