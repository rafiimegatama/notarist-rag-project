package com.notarist.runtime.provider.registry;

import com.notarist.runtime.provider.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Holds every {@link EmbeddingProvider} on the classpath and exposes the one selected by
 * {@code notarist.runtime.embedding.provider} (env {@code EMBED_PROVIDER}).
 */
@Component
public class EmbeddingRegistry extends AbstractRuntimeRegistry<EmbeddingProvider> {

    public EmbeddingRegistry(List<EmbeddingProvider> providers,
                             @Value("${notarist.runtime.embedding.provider:ollama}") String activeId) {
        super("EMBEDDING", providers, activeId);
    }
}
