package com.notarist.runtime.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Central model registry. Single source of truth for all AI model configurations.
 *
 * No model config scattered across adapters.
 * All runtime adapters inject ModelRegistry to resolve endpoints, dimensions, and context limits.
 *
 * Endpoint URLs are injected from the notarist.sidecar.* configuration block in
 * application.yaml (env-overridable: OLLAMA_BASE_URL, OCR_BASE_URL, NER_BASE_URL,
 * RERANKER_BASE_URL, EMBEDDING_BASE_URL) so the registry always matches the
 * deployed sidecar topology instead of compile-time constants.
 */
@Component
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private final Map<ModelProvider, ModelDefinition> registry;

    public ModelRegistry(
            @Value("${notarist.sidecar.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${notarist.sidecar.embedding.base-url:http://localhost:8084}") String embeddingUrl,
            @Value("${notarist.sidecar.reranker.base-url:http://localhost:8083}") String rerankerUrl,
            // Resolve OCR from the SAME property the real provider (PaddleOcrProvider/OcrProperties)
            // uses, so the startup probe and runtime traffic can never target different hosts. Falls
            // back to the legacy sidecar key, then localhost.
            @Value("${notarist.ocr.providers.paddle.endpoint:${notarist.sidecar.ocr.base-url:http://localhost:8081}}") String ocrUrl,
            @Value("${notarist.sidecar.ner.base-url:http://localhost:8082}") String nerUrl) {
        registry = Map.of(
                ModelProvider.OLLAMA, new ModelDefinition(
                        "llama3.2:3b-instruct-q8_0", "3.2.0", ModelProvider.OLLAMA,
                        ollamaUrl, 0, "llama", 131072, 2048, "unverified"),
                ModelProvider.BGE_M3, new ModelDefinition(
                        "bge-m3", "1.0.0", ModelProvider.BGE_M3,
                        embeddingUrl, 1024, "bge-m3-tokenizer", 8192, 0, "unverified"),
                ModelProvider.CROSS_ENCODER, new ModelDefinition(
                        "bge-reranker-v2-m3", "2.0.0", ModelProvider.CROSS_ENCODER,
                        rerankerUrl, 0, "bge-reranker-tokenizer", 512, 0, "unverified"),
                ModelProvider.PADDLEOCR, new ModelDefinition(
                        "paddleocr-v4", "4.0.0", ModelProvider.PADDLEOCR,
                        ocrUrl, 0, "none", 0, 0, "unverified"),
                ModelProvider.INDOBERT, new ModelDefinition(
                        "indobert-base", "1.0.0", ModelProvider.INDOBERT,
                        nerUrl, 0, "indobert-tokenizer", 512, 0, "unverified"));
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
    public ModelDefinition getNer()      { return get(ModelProvider.INDOBERT); }

    public int embeddingDimension() {
        return get(ModelProvider.BGE_M3).embeddingDimension();
    }

    public int llmMaxContextWindow() {
        return get(ModelProvider.OLLAMA).maxContextWindow();
    }
}
