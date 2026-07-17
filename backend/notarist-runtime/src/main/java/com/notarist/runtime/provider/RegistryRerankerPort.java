package com.notarist.runtime.provider;

import com.notarist.runtime.provider.registry.RuntimeRegistry;
import com.notarist.search.application.port.out.RerankerPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The single {@link RerankerPort} bean the search pipeline sees. Delegates to the registry-selected
 * {@link RerankerProvider} ({@code crossencoder}, {@code none}, …), so {@code RerankerService}
 * (business logic) is decoupled from whether — and how — reranking is performed.
 */
@Component
public class RegistryRerankerPort implements RerankerPort {

    private final RuntimeRegistry registry;

    public RegistryRerankerPort(RuntimeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        return registry.reranker().rerank(query, candidates);
    }
}
