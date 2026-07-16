package com.notarist.runtime.ocr.spi;

import java.time.Instant;
import java.util.Map;

/**
 * The result of probing an OCR engine.
 *
 * <p>{@code UNKNOWN} exists on purpose and is not the same as {@code DOWN}. "I have not probed this
 * engine yet" and "I probed it and it is broken" are different facts, and collapsing them means the
 * health endpoint reports an outage every time the app starts.
 */
public record OcrProviderHealth(
        String providerId,
        Status status,
        String detail,
        Instant checkedAt,
        Map<String, Object> attributes
) {

    public enum Status {
        /** Probed, reachable, ready. */
        UP,
        /** Probed and failed. */
        DOWN,
        /** Not probed yet, or the engine offers no probe. Not an outage. */
        UNKNOWN
    }

    public OcrProviderHealth {
        if (attributes == null) {
            attributes = Map.of();
        }
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }

    public static OcrProviderHealth up(String providerId, String detail) {
        return new OcrProviderHealth(providerId, Status.UP, detail, Instant.now(), Map.of());
    }

    public static OcrProviderHealth up(String providerId, String detail, Map<String, Object> attributes) {
        return new OcrProviderHealth(providerId, Status.UP, detail, Instant.now(), attributes);
    }

    public static OcrProviderHealth down(String providerId, String detail) {
        return new OcrProviderHealth(providerId, Status.DOWN, detail, Instant.now(), Map.of());
    }

    public static OcrProviderHealth unknown(String providerId, String detail) {
        return new OcrProviderHealth(providerId, Status.UNKNOWN, detail, Instant.now(), Map.of());
    }

    public boolean isUp() {
        return status == Status.UP;
    }
}
