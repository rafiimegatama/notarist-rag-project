package com.notarist.runtime.reranker;

import com.notarist.runtime.provider.ProviderCapabilities;
import com.notarist.runtime.provider.RerankerProvider;
import com.notarist.runtime.provider.RuntimeProviderHealth;
import com.notarist.search.application.port.out.RerankerPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code none} reranker provider — the default. Returns candidates in their original
 * (fusion/retrieval) order without a second-stage model. This is the correct behaviour when no
 * reranker is deployed: the hybrid retrieval ranking already produced a usable order, and calling a
 * non-existent reranker endpoint would only add latency and failures.
 *
 * <p>Select a real reranker with {@code RERANK_PROVIDER=crossencoder}.
 */
@Component
public class NoneRerankerProvider implements RerankerProvider {

    private static final String PROVIDER_ID = "none";

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public String activeModel() {
        return "none";
    }

    @Override
    public ProviderCapabilities capabilities() {
        // Advertises nothing: it does not actually rerank, it preserves order. Honest capabilities
        // let the runtime/health report reflect that reranking is effectively off.
        return ProviderCapabilities.builder().build();
    }

    @Override
    public RuntimeProviderHealth health() {
        return RuntimeProviderHealth.up(PROVIDER_ID, "none", "passthrough — retrieval order preserved");
    }

    @Override
    public List<RerankerPort.RerankResult> rerank(String query, List<RerankerPort.RerankCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        // Preserve incoming order; a descending score keeps any downstream sort stable.
        int n = candidates.size();
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> new RerankerPort.RerankResult(candidates.get(i).chunkId(), (double) (n - i)))
                .toList();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
