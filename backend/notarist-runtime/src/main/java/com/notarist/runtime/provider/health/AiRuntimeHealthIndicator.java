package com.notarist.runtime.provider.health;

import com.notarist.runtime.capability.RuntimeCapabilityDetector;
import com.notarist.runtime.provider.RuntimeProvider;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import com.notarist.runtime.provider.registry.RuntimeRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator health indicator for the unified AI runtime (Phase 7). Surfaces, under
 * {@code /actuator/health} as {@code aiRuntime}:
 *
 * <ul>
 *   <li>current provider + model for LLM / embedding / reranker</li>
 *   <li>provider availability and probe status (UP/DOWN/UNKNOWN)</li>
 *   <li>streaming availability (from the active LLM provider's capabilities)</li>
 *   <li>embedding availability</li>
 *   <li>GPU availability (CUDA, VRAM, cores) from {@link RuntimeCapabilityDetector}</li>
 * </ul>
 *
 * <p>Reports DOWN only when the active <b>LLM</b> provider is down — that is the user-facing
 * capability. A degraded embedding path stops new ingestion but existing search still serves, so it
 * is reported as detail, matching {@code RuntimeDegradationManager}'s operation-mode model.
 */
@Component("aiRuntimeHealthIndicator")
public class AiRuntimeHealthIndicator implements HealthIndicator {

    private final RuntimeRegistry registry;
    private final RuntimeCapabilityDetector.RuntimeCapability capability;

    public AiRuntimeHealthIndicator(RuntimeRegistry registry,
                                    RuntimeCapabilityDetector.RuntimeCapability capability) {
        this.registry = registry;
        this.capability = capability;
    }

    @Override
    public Health health() {
        RuntimeProviderHealth llm       = safe(registry.llmRegistry()::activeHealth);
        RuntimeProviderHealth embedding = safe(registry.embeddingRegistry()::activeHealth);
        RuntimeProviderHealth reranker  = safe(registry.rerankerRegistry()::activeHealth);

        boolean streamingAvailable =
                registry.llm().capabilities().supportsStreaming() && llm.isUp();

        Health.Builder builder = llm.isUp() ? Health.up() : Health.down();
        return builder
                .withDetail("llm", describe(registry.llm(), llm))
                .withDetail("embedding", describe(registry.embedding(), embedding))
                .withDetail("reranker", describe(registry.reranker(), reranker))
                .withDetail("streaming.available", streamingAvailable)
                .withDetail("embedding.available", embedding.isUp())
                .withDetail("gpu", gpu())
                .build();
    }

    private Map<String, Object> describe(RuntimeProvider provider, RuntimeProviderHealth health) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", provider.id());
        m.put("model", provider.activeModel());
        m.put("status", health.status().name());
        m.put("available", provider.isAvailable());
        m.put("detail", health.detail());
        m.put("capabilities", capabilitiesOf(provider));
        if (!health.attributes().isEmpty()) {
            m.put("attributes", health.attributes());
        }
        return m;
    }

    private Map<String, Object> capabilitiesOf(RuntimeProvider provider) {
        var c = provider.capabilities();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("streaming", c.supportsStreaming());
        m.put("vision", c.supportsVision());
        m.put("toolCalling", c.supportsToolCalling());
        m.put("jsonMode", c.supportsJsonMode());
        m.put("embedding", c.supportsEmbedding());
        m.put("reranking", c.supportsReranking());
        m.put("thinking", c.supportsThinking());
        m.put("batch", c.supportsBatchInference());
        m.put("maxBatchSize", c.maxBatchSize());
        return m;
    }

    private Map<String, Object> gpu() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cudaAvailable", capability.cudaAvailable());
        m.put("vramMb", capability.vramMb());
        m.put("cpuCores", capability.cpuCores());
        m.put("freeHeapMb", capability.freeMemoryMb());
        return m;
    }

    private RuntimeProviderHealth safe(java.util.function.Supplier<RuntimeProviderHealth> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return RuntimeProviderHealth.down("unknown", null, "registry error: " + e.getMessage());
        }
    }
}
