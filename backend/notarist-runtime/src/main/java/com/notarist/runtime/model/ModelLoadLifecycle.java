package com.notarist.runtime.model;

import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Manages AI model health verification and warm-up on application startup.
 *
 * On ApplicationReadyEvent:
 *   1. Verify each model endpoint is reachable (lightweight health check)
 *   2. Warm up Ollama: send a minimal prompt to pre-load model into memory
 *   3. Warm up embedding: send a dummy text to pre-load bge-m3
 *   4. Log model status summary
 *
 * Failures during warm-up are logged as WARN — they do not crash startup.
 * RuntimeDegradationManager is updated to reflect which services are unavailable.
 */
@Component
public class ModelLoadLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ModelLoadLifecycle.class);
    private static final String WARMUP_TEXT = "notarist warmup";

    private final ModelRegistry             modelRegistry;
    private final RuntimeDegradationManager degradationManager;
    private final RuntimeMetricsRegistry    metricsRegistry;
    private final RestTemplate              restTemplate;

    public ModelLoadLifecycle(
            ModelRegistry modelRegistry,
            RuntimeDegradationManager degradationManager,
            RuntimeMetricsRegistry metricsRegistry,
            @Qualifier("aiRuntimeRestTemplate") RestTemplate restTemplate) {
        this.modelRegistry      = modelRegistry;
        this.degradationManager = degradationManager;
        this.metricsRegistry    = metricsRegistry;
        this.restTemplate       = restTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("ModelLoadLifecycle: verifying AI runtime endpoints...");
        verifyOllama();
        verifyEmbedding();
        verifyReranker();
        verifyOcr();
        log.info("ModelLoadLifecycle: startup check complete. Active mode: {}",
                degradationManager.getActiveMode());
    }

    private void verifyOllama() {
        ModelDefinition model = modelRegistry.getLlm();
        long startMs = System.currentTimeMillis();
        try {
            // Ollama list models endpoint — lightweight, no inference
            var response = restTemplate.getForEntity(model.endpointUrl() + "/api/tags", Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                metricsRegistry.recordModelLoadTime(ModelProvider.OLLAMA, System.currentTimeMillis() - startMs);
                degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.OLLAMA, false, null);
                log.info("Ollama OK: {} @ {}", model.modelName(), model.endpointUrl());
            }
        } catch (Exception e) {
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.OLLAMA, true, e.getMessage());
            log.warn("Ollama UNAVAILABLE @ {}: {}", model.endpointUrl(), e.getMessage());
        }
    }

    private void verifyEmbedding() {
        ModelDefinition model = modelRegistry.getEmbedding();
        long startMs = System.currentTimeMillis();
        try {
            var body = Map.of("texts", new String[]{WARMUP_TEXT});
            restTemplate.postForEntity(model.endpointUrl() + "/embed", body, Map.class);
            metricsRegistry.recordModelLoadTime(ModelProvider.BGE_M3, System.currentTimeMillis() - startMs);
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, false, null);
            log.info("Embedding OK: {} @ {}", model.modelName(), model.endpointUrl());
        } catch (Exception e) {
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.EMBEDDING, true, e.getMessage());
            log.warn("Embedding UNAVAILABLE @ {}: {}", model.endpointUrl(), e.getMessage());
        }
    }

    private void verifyReranker() {
        ModelDefinition model = modelRegistry.getReranker();
        try {
            var body = Map.of("query", WARMUP_TEXT, "texts", new String[]{WARMUP_TEXT});
            restTemplate.postForEntity(model.endpointUrl() + "/rerank", body, Map.class);
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.RERANKER, false, null);
            log.info("Reranker OK: {} @ {}", model.modelName(), model.endpointUrl());
        } catch (Exception e) {
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.RERANKER, true, e.getMessage());
            log.warn("Reranker UNAVAILABLE @ {}: {}", model.endpointUrl(), e.getMessage());
        }
    }

    private void verifyOcr() {
        ModelDefinition model = modelRegistry.getOcr();
        try {
            restTemplate.getForEntity(model.endpointUrl() + "/health", String.class);
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, false, null);
            log.info("OCR OK: {} @ {}", model.modelName(), model.endpointUrl());
        } catch (Exception e) {
            degradationManager.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, true, e.getMessage());
            log.warn("OCR UNAVAILABLE @ {}: {}", model.endpointUrl(), e.getMessage());
        }
    }
}
