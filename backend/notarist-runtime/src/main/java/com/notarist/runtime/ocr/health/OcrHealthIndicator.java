package com.notarist.runtime.ocr.health;

import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.registry.OcrProviderRegistry;
import com.notarist.runtime.ocr.runtime.OcrBatchSizer;
import com.notarist.runtime.ocr.spi.OcrCapabilities;
import com.notarist.runtime.ocr.spi.OcrProvider;
import com.notarist.runtime.ocr.spi.OcrProviderHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contributes an {@code ocr} section to {@code /actuator/health}.
 *
 * <p>It answers the questions you actually have at 3am: which engine is running, where is it, is it
 * reachable, is the runtime treating it as degraded, and what batch size did it pick on this
 * hardware. Reporting only UP/DOWN would tell you the OCR stage is broken without telling you
 * whether that is because the endpoint is wrong, the engine is down, or the circuit is open.
 *
 * <p>The active provider being DOWN makes this indicator DOWN — but the alternate providers are
 * reported too, because "paddle is down and surya is up" is the sentence that decides whether the
 * fix is a config flip or an incident.
 */
@Component("ocrRuntimeHealthIndicator")
public class OcrHealthIndicator implements HealthIndicator {

    private final OcrProviderRegistry registry;
    private final OcrProperties properties;
    private final OcrBatchSizer batchSizer;
    private final RuntimeDegradationManager degradation;

    public OcrHealthIndicator(
            OcrProviderRegistry registry,
            OcrProperties properties,
            OcrBatchSizer batchSizer,
            RuntimeDegradationManager degradation) {
        this.registry = registry;
        this.properties = properties;
        this.batchSizer = batchSizer;
        this.degradation = degradation;
    }

    @Override
    public Health health() {
        OcrProvider active = registry.active();
        OcrCapabilities capabilities = active.capabilities();
        OcrProviderHealth activeHealth = registry.activeHealth();
        boolean degraded = degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OCR);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("activeProvider", active.id());
        details.put("displayName", active.displayName());
        details.put("providerStatus", activeHealth.status().name());
        details.put("detail", activeHealth.detail());

        // The runtime's own view. An engine can be reachable while the circuit is open from an
        // earlier burst of failures — those two facts disagreeing is itself the diagnosis.
        details.put("runtimeDegraded", degraded);

        details.put("timeoutMs", properties.effectiveTimeoutMs(active.id()));
        details.put("retryMaxAttempts", properties.getRetry().getMaxAttempts());

        details.put("hardwareProfile", batchSizer.hardwareProfile().name());
        details.put("effectiveBatchSize", batchSizer.resolveFor(capabilities));
        details.put("batchSupported", capabilities.supportsBatch());

        details.put("availableProviders", registry.availableIds());

        // The capability matrix. "Could we switch to Gemini for handwriting?" should be answerable
        // from one endpoint, not by reading six adapters.
        details.put("capabilityMatrix", registry.capabilityMatrix());

        // Every engine's health, not just the active one — so a failover decision does not need a
        // second round-trip to find out whether the alternative is even up.
        Map<String, String> allProviders = new LinkedHashMap<>();
        registry.healthOfAll().forEach((id, health) ->
                allProviders.put(id, health.status().name() + " — " + health.detail()));
        details.put("providers", allProviders);

        boolean healthy = activeHealth.isUp() && !degraded;
        return (healthy ? Health.up() : Health.down()).withDetails(details).build();
    }
}
