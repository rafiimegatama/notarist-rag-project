package com.notarist.runtime.ocr.runtime;

import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.spi.OcrProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Exponential backoff with jitter, applied ONCE, here, for every OCR engine.
 *
 * <p>Retry lives in the runtime rather than in each provider on purpose. If providers retried
 * internally the policies would multiply (an outer 3 × an inner 3 = 9 attempts) against a GPU that
 * is, by hypothesis, already overloaded — and each engine would grow its own subtly different
 * backoff curve to debug.
 *
 * <p><b>It only retries what the provider says is retryable.</b> A corrupt PDF throws
 * {@code permanent} and fails on the first attempt, reaching the dead-letter queue in seconds where
 * a human can see it. Retrying it would burn every attempt and every backoff to arrive at the same
 * answer, several minutes later. Anything that is not an {@link OcrProviderException} is treated as
 * permanent: an unclassified error is one nobody has reasoned about, and guessing "retryable"
 * turns an NPE into three NPEs.
 *
 * <p>Jitter matters more than it looks. A batch of documents failing together against a saturated
 * engine would otherwise retry in lockstep and saturate it again, in phase, forever.
 */
@Component
public class OcrRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(OcrRetryPolicy.class);

    private final OcrProperties properties;

    public OcrRetryPolicy(OcrProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String operation, Supplier<T> call) {
        OcrProperties.Retry config = properties.getRetry();
        int maxAttempts = Math.max(1, config.getMaxAttempts());

        OcrProviderException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();

            } catch (OcrProviderException e) {
                last = e;

                if (!e.isRetryable()) {
                    log.warn("OCR {}: permanent failure from provider '{}' on attempt {} — not retrying: {}",
                            operation, e.providerId(), attempt, e.getMessage());
                    throw e;
                }

                if (attempt == maxAttempts) {
                    log.error("OCR {}: provider '{}' still failing after {} attempt(s): {}",
                            operation, e.providerId(), maxAttempts, e.getMessage());
                    throw e;
                }

                long backoffMs = backoffFor(attempt, config);
                log.warn("OCR {}: retryable failure from provider '{}' on attempt {}/{} ({}). "
                                + "Retrying in {}ms.",
                        operation, e.providerId(), attempt, maxAttempts, e.getMessage(), backoffMs);
                sleep(backoffMs);

            } catch (RuntimeException e) {
                // Not an OcrProviderException: nobody has decided whether this is worth retrying, so
                // do not decide for them. Surface it.
                log.error("OCR {}: unclassified failure on attempt {} — treating as permanent: {}",
                        operation, attempt, e.getMessage(), e);
                throw e;
            }
        }

        throw last; // unreachable: the loop either returns or throws.
    }

    /** Exponential growth, capped, then randomised by ±jitterFactor. Never negative. */
    long backoffFor(int attempt, OcrProperties.Retry config) {
        double raw = config.getInitialBackoffMs() * Math.pow(config.getMultiplier(), attempt - 1.0);
        double capped = Math.min(raw, config.getMaxBackoffMs());

        double jitter = config.getJitterFactor();
        if (jitter <= 0) {
            return (long) capped;
        }

        double delta = capped * jitter;
        double randomised = capped + ThreadLocalRandom.current().nextDouble(-delta, delta);
        return (long) Math.max(0, randomised);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Preserve the interrupt: the OCR pool is shutting down, and swallowing this would keep
            // the thread alive through shutdown, retrying against an engine nobody is waiting for.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR retry interrupted during backoff", e);
        }
    }
}
