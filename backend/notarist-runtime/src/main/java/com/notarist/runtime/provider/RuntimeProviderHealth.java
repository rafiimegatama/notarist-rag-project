package com.notarist.runtime.provider;

import java.time.Instant;
import java.util.Map;

/**
 * The result of probing an AI runtime provider (LLM / embedding / reranker).
 *
 * <p>Deliberately identical in shape to {@code com.notarist.runtime.ocr.spi.OcrProviderHealth}:
 * {@code UNKNOWN} is a first-class state, distinct from {@code DOWN}. "Not probed yet" and "probed
 * and broken" are different facts; collapsing them makes the health endpoint cry outage on every
 * cold start.
 *
 * @param providerId which provider this describes
 * @param status     UP / DOWN / UNKNOWN
 * @param detail     human-readable explanation for logs and the actuator payload
 * @param model      the model the provider is currently configured to serve (nullable)
 * @param checkedAt  when the probe ran
 * @param attributes provider-specific extras (loaded models, latency, GPU, …)
 */
public record RuntimeProviderHealth(
        String providerId,
        Status status,
        String detail,
        String model,
        Instant checkedAt,
        Map<String, Object> attributes
) {

    public enum Status { UP, DOWN, UNKNOWN }

    public RuntimeProviderHealth {
        if (attributes == null) attributes = Map.of();
        if (checkedAt == null)  checkedAt = Instant.now();
    }

    public static RuntimeProviderHealth up(String providerId, String model, String detail) {
        return new RuntimeProviderHealth(providerId, Status.UP, detail, model, Instant.now(), Map.of());
    }

    public static RuntimeProviderHealth up(String providerId, String model, String detail, Map<String, Object> attributes) {
        return new RuntimeProviderHealth(providerId, Status.UP, detail, model, Instant.now(), attributes);
    }

    public static RuntimeProviderHealth down(String providerId, String model, String detail) {
        return new RuntimeProviderHealth(providerId, Status.DOWN, detail, model, Instant.now(), Map.of());
    }

    public static RuntimeProviderHealth unknown(String providerId, String model, String detail) {
        return new RuntimeProviderHealth(providerId, Status.UNKNOWN, detail, model, Instant.now(), Map.of());
    }

    public boolean isUp() {
        return status == Status.UP;
    }
}
