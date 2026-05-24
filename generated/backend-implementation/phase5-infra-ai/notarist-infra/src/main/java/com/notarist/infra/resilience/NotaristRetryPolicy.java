package com.notarist.infra.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Retry policy for all external integration calls.
 *
 * Strategy:
 *   - maxAttempts: 3
 *   - Backoff: base 1000ms, multiplied by 2^attempt (capped at 10s)
 *   - Retryable: any Exception (callers should not retry on 4xx; use try-catch for that)
 *   - Non-retryable: IllegalArgumentException, IllegalStateException (programming errors)
 *
 * Each operation name is tagged in retry metrics for observability.
 * Use execute(String, Callable) — the operation name identifies the integration point.
 */
@Component
public class NotaristRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(NotaristRetryPolicy.class);

    private static final int  MAX_ATTEMPTS    = 3;
    private static final long BACKOFF_BASE_MS = 1_000;
    private static final long BACKOFF_MAX_MS  = 10_000;

    private final MeterRegistry meterRegistry;

    public NotaristRetryPolicy(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(String operation, Callable<T> action) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_ATTEMPTS) {
            try {
                return action.call();
            } catch (IllegalArgumentException | IllegalStateException e) {
                // Programming errors — do not retry
                throw new IntegrationException(operation + " failed (non-retryable)", e);
            } catch (Exception e) {
                attempt++;
                lastException = e;
                recordRetry(operation, attempt);

                if (attempt < MAX_ATTEMPTS) {
                    long backoffMs = Math.min(BACKOFF_BASE_MS * (1L << (attempt - 1)), BACKOFF_MAX_MS);
                    log.warn("Retry {}/{} for '{}' after {}ms: {}", attempt, MAX_ATTEMPTS, operation, backoffMs, e.getMessage());
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IntegrationException(operation + " interrupted during retry", ie);
                    }
                }
            }
        }

        recordExhausted(operation);
        throw new IntegrationException(operation + " failed after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    private void recordRetry(String operation, int attempt) {
        Counter.builder("notarist.integration.retries")
                .tag("operation", operation)
                .tag("attempt", String.valueOf(attempt))
                .register(meterRegistry)
                .increment();
    }

    private void recordExhausted(String operation) {
        Counter.builder("notarist.integration.retries.exhausted")
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    public static class IntegrationException extends RuntimeException {
        public IntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
