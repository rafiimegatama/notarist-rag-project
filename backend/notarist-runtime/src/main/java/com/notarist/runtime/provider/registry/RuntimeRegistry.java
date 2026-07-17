package com.notarist.runtime.provider.registry;

import com.notarist.runtime.provider.EmbeddingProvider;
import com.notarist.runtime.provider.InferenceProvider;
import com.notarist.runtime.provider.RerankerProvider;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one unified runtime registry (Phase 2). A thin facade over the per-kind registries:
 *
 * <pre>
 *   RuntimeRegistry
 *   ├── LlmRegistry
 *   ├── EmbeddingRegistry
 *   ├── RerankerRegistry
 *   └── (future) OCRRegistry — already implemented separately as OcrProviderRegistry; wire here
 *                              when that module stabilises. Kept decoupled for now so this file
 *                              never touches OCR code.
 * </pre>
 *
 * <p>Every accessor returns an <b>interface</b>. Business logic reaches inference through the
 * application ports; the {@code Registry*Port} routers resolve the active provider here. Nothing
 * outside this package instantiates a provider.
 */
@Component
public class RuntimeRegistry {

    private final LlmRegistry llm;
    private final EmbeddingRegistry embedding;
    private final RerankerRegistry reranker;

    public RuntimeRegistry(LlmRegistry llm, EmbeddingRegistry embedding, RerankerRegistry reranker) {
        this.llm = llm;
        this.embedding = embedding;
        this.reranker = reranker;
    }

    /** Active LLM provider (interface only). */
    public InferenceProvider llm() {
        return llm.active();
    }

    /** Active embedding provider (interface only). */
    public EmbeddingProvider embedding() {
        return embedding.active();
    }

    /** Active reranker provider (interface only). */
    public RerankerProvider reranker() {
        return reranker.active();
    }

    public LlmRegistry llmRegistry()             { return llm; }
    public EmbeddingRegistry embeddingRegistry() { return embedding; }
    public RerankerRegistry rerankerRegistry()   { return reranker; }

    /** Health of the active provider for every kind — for the runtime health endpoint. */
    public Map<String, RuntimeProviderHealth> activeHealthByKind() {
        Map<String, RuntimeProviderHealth> out = new LinkedHashMap<>();
        out.put("llm", llm.activeHealth());
        out.put("embedding", embedding.activeHealth());
        out.put("reranker", reranker.activeHealth());
        return out;
    }
}
