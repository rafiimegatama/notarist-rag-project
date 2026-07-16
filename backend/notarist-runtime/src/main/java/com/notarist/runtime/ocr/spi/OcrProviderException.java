package com.notarist.runtime.ocr.spi;

/**
 * An OCR engine failed.
 *
 * <p>{@code retryable} is the important field and providers must set it honestly, because the retry
 * policy trusts it completely:
 *
 * <ul>
 *   <li><b>retryable</b> — the engine was unreachable, overloaded, returned 5xx, or timed out. The
 *       same request may well succeed in a moment.</li>
 *   <li><b>NOT retryable</b> — the document is corrupt, the language is unsupported, credentials are
 *       wrong, the request was rejected 4xx. Retrying burns GPU time and delays the job reaching its
 *       dead-letter queue, where a human can actually see it.</li>
 * </ul>
 *
 * <p>Defaulting to retryable "just in case" is the expensive mistake: a corrupt PDF then consumes
 * every attempt and every backoff before failing anyway.
 */
public class OcrProviderException extends RuntimeException {

    private final String providerId;
    private final boolean retryable;

    public OcrProviderException(String providerId, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.retryable = retryable;
    }

    /** The engine is unreachable, overloaded or timed out — worth another attempt. */
    public static OcrProviderException retryable(String providerId, String message, Throwable cause) {
        return new OcrProviderException(providerId, message, true, cause);
    }

    /** The request itself is bad. Another attempt will fail identically. */
    public static OcrProviderException permanent(String providerId, String message, Throwable cause) {
        return new OcrProviderException(providerId, message, false, cause);
    }

    public String providerId() {
        return providerId;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
