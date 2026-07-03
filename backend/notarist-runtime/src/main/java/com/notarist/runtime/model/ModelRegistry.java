package com.notarist.runtime.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central model registry. Single source of truth for all AI model configurations.
 *
 * No model config scattered across adapters.
 * All runtime adapters inject ModelRegistry to resolve endpoints, dimensions, and context limits.
 *
 * Bound from spring.notarist.models.* in application.yml.
 * Example:
 *   spring:
 *     notarist:
 *       models:
 *         - model-name: llama3.2:3b-instruct-q8_0
 *           version: "3.2.0"
 *           provider: OLLAMA
 *           endpoint-url: http://localhost:11434
 *           max-context-window: 131072
 *           max-output-tokens: 2048
 */
@Component
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    /** Built-in defaults for Phase 5B — overridable via application.yml */
    private static final ModelDefinition DEFAULT_LLM = new ModelDefinition(
            "llama3.2:3b-instruct-q8_0", "3.2.0", ModelProvider.OLLAMA,
            "http://localhost:11434", 0, "llama", 131072, 2048, "unverified");

    private static final ModelDefinition DEFAULT_EMBEDDING = new ModelDefinition(
            "bge-m3", "1.0.0", ModelProvider.BGE_M3,
            "http://localhost:8100", 1024, "bge-m3-tokenizer", 8192, 0, "unverified");

    private static final ModelDefinition DEFAULT_RERANKER = new ModelDefinition(
            "bge-reranker-v2-m3", "2.0.0", ModelProvider.CROSS_ENCODER,
            "http://localhost:8101", 0, "bge-reranker-tokenizer", 512, 0, "unverified");

    private static final ModelDefinition DEFAULT_OCR = new ModelDefinition(
            "paddleocr-v4", "4.0.0", ModelProvider.PADDLEOCR,
            "http://localhost:9080", 0, "none", 0, 0, "unverified");

    private final Map<ModelProvider, ModelDefinition> registry;

    public ModelRegistry() {
        registry = Map.of(
                ModelProvider.OLLAMA,       DEFAULT_LLM,
                ModelProvider.BGE_M3,       DEFAULT_EMBEDDING,
                ModelProvider.CROSS_ENCODER, DEFAULT_RERANKER,
                ModelProvider.PADDLEOCR,    DEFAULT_OCR);
        log.info("ModelRegistry initialized with {} models: {}",
                registry.size(), registry.keySet());
    }

    public ModelDefinition get(ModelProvider provider) {
        ModelDefinition def = registry.get(provider);
        if (def == null) throw new IllegalArgumentException("No model registered for provider: " + provider);
        return def;
    }

    public Optional<ModelDefinition> find(ModelProvider provider) {
        return Optional.ofNullable(registry.get(provider));
    }

    public ModelDefinition getLlm()      { return get(ModelProvider.OLLAMA); }
    public ModelDefinition getEmbedding(){ return get(ModelProvider.BGE_M3); }
    public ModelDefinition getReranker() { return get(ModelProvider.CROSS_ENCODER); }
    public ModelDefinition getOcr()      { return get(ModelProvider.PADDLEOCR); }

    public int embeddingDimension() {
        return get(ModelProvider.BGE_M3).embeddingDimension();
    }

    public int llmMaxContextWindow() {
        return get(ModelProvider.OLLAMA).maxContextWindow();
    }
}
