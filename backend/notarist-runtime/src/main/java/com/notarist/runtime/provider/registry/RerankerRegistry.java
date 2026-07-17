package com.notarist.runtime.provider.registry;

import com.notarist.runtime.provider.RerankerProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Holds every {@link RerankerProvider} on the classpath and exposes the one selected by
 * {@code notarist.runtime.reranker.provider} (env {@code RERANK_PROVIDER}). Defaults to
 * {@code none} (keep retrieval order).
 */
@Component
public class RerankerRegistry extends AbstractRuntimeRegistry<RerankerProvider> {

    public RerankerRegistry(List<RerankerProvider> providers,
                            @Value("${notarist.runtime.reranker.provider:none}") String activeId) {
        super("RERANKER", providers, activeId);
    }
}
