package com.notarist.runtime.provider.registry;

import com.notarist.runtime.provider.InferenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Holds every {@link InferenceProvider} on the classpath and exposes the one selected by
 * {@code notarist.runtime.llm.provider} (env {@code LLM_PROVIDER}). Exposes only the interface —
 * business logic never sees a concrete provider type.
 */
@Component
public class LlmRegistry extends AbstractRuntimeRegistry<InferenceProvider> {

    public LlmRegistry(List<InferenceProvider> providers,
                       @Value("${notarist.runtime.llm.provider:ollama}") String activeId) {
        super("LLM", providers, activeId);
    }
}
