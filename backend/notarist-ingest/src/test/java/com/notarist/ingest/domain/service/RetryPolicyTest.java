package com.notarist.ingest.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the documented backoff contract of {@link RetryPolicy}.
 *
 * <p>Regression guard (F19): the implementation computed {@code BASE * 2^attemptCount}, so the
 * first retry (attempt=1) waited 60s while the javadoc promised 30s — an off-by-one against the
 * contract every caller reads. PipelineCoordinator calls
 * {@code computeNextRetryAt(job.getRetryCount() + 1)}, so a job that has never been retried
 * (retryCount=0) hits attempt=1 and MUST wait BASE_DELAY (30s), not 2x BASE_DELAY.
 */
class RetryPolicyTest {

    /** Tolerance for the Instant.now() taken inside computeNextRetryAt. */
    private static final long TOLERANCE_SECONDS = 5L;

    private static long delaySecondsFor(int attemptCount) {
        Instant before = Instant.now();
        Instant nextRetryAt = RetryPolicy.computeNextRetryAt(attemptCount);
        return nextRetryAt.getEpochSecond() - before.getEpochSecond();
    }

    private static void assertDelay(long expectedSeconds, int attemptCount) {
        long actual = delaySecondsFor(attemptCount);
        long drift = Math.abs(actual - expectedSeconds);
        assertTrue(drift <= TOLERANCE_SECONDS,
                "attempt=" + attemptCount + " expected ~" + expectedSeconds + "s but was " + actual + "s");
    }

    @Test
    @DisplayName("backoff matches the documented schedule: attempt=1 -> 30s, 2 -> 60s, 3 -> 120s")
    void backoffMatchesDocumentedSchedule() {
        assertDelay(30L, 1);
        assertDelay(60L, 2);
        assertDelay(120L, 3);
        assertDelay(240L, 4);
        assertDelay(480L, 5);
    }

    @Test
    @DisplayName("first retry of a never-retried job (PipelineCoordinator: retryCount 0 + 1) waits BASE_DELAY = 30s")
    void firstRetryOfFreshJobWaitsBaseDelay() {
        int retryCountOfFreshJob = 0;
        assertDelay(30L, retryCountOfFreshJob + 1);
    }

    @Test
    @DisplayName("delay is capped and never exceeds MAX_DELAY (3600s), however high the attempt count")
    void delayIsCapped() {
        assertTrue(delaySecondsFor(10) <= 3600L + TOLERANCE_SECONDS);
        assertTrue(delaySecondsFor(64) <= 3600L + TOLERANCE_SECONDS);
        assertTrue(delaySecondsFor(Integer.MAX_VALUE) <= 3600L + TOLERANCE_SECONDS);
        // the shift is clamped, so growth stops rather than overflowing into a negative delay
        assertTrue(delaySecondsFor(Integer.MAX_VALUE) > 0L);
    }

    @Test
    @DisplayName("attempt=0 or negative degrades to BASE_DELAY instead of an under/overflowing shift")
    void nonPositiveAttemptDegradesToBaseDelay() {
        assertDelay(30L, 0);
        assertDelay(30L, -1);
    }

    @Test
    @DisplayName("the custom-base overload follows the same schedule: base * 2^(attempt-1)")
    void customBaseOverloadFollowsSameSchedule() {
        Instant before = Instant.now();
        long actual = RetryPolicy.computeNextRetryAt(1, 10L).getEpochSecond() - before.getEpochSecond();
        assertTrue(Math.abs(actual - 10L) <= TOLERANCE_SECONDS, "expected ~10s, was " + actual + "s");

        before = Instant.now();
        actual = RetryPolicy.computeNextRetryAt(3, 10L).getEpochSecond() - before.getEpochSecond();
        assertTrue(Math.abs(actual - 40L) <= TOLERANCE_SECONDS, "expected ~40s, was " + actual + "s");
    }

    /**
     * shouldRetry semantics are load-bearing for PipelineCoordinator
     * ({@code shouldRetry(job.getRetryCount(), maxRetries)}) and must NOT shift with the
     * backoff fix: a job that has already been retried maxRetries times is done.
     */
    @Test
    @DisplayName("shouldRetry compares retryCount (not attempt number) against maxRetries — unchanged by the backoff fix")
    void shouldRetryCountSemanticsUnchanged() {
        assertTrue(RetryPolicy.shouldRetry(0, 3));
        assertTrue(RetryPolicy.shouldRetry(2, 3));
        assertFalse(RetryPolicy.shouldRetry(3, 3));
        assertFalse(RetryPolicy.shouldRetry(4, 3));
    }

    @Test
    @DisplayName("isReadyForRetry: null nextRetryAt is ready; a future instant is not")
    void isReadyForRetrySemantics() {
        assertTrue(RetryPolicy.isReadyForRetry(null));
        assertTrue(RetryPolicy.isReadyForRetry(Instant.now().minusSeconds(60)));
        assertFalse(RetryPolicy.isReadyForRetry(Instant.now().plusSeconds(60)));
    }

    @Test
    @DisplayName("sanity: the schedule is monotonically non-decreasing")
    void scheduleIsMonotonic() {
        long previous = 0L;
        for (int attempt = 1; attempt <= 12; attempt++) {
            long current = delaySecondsFor(attempt);
            assertTrue(current >= previous - TOLERANCE_SECONDS,
                    "attempt=" + attempt + " delay " + current + "s regressed below " + previous + "s");
            previous = current;
        }
        assertEquals(true, previous > 0L);
    }
}
