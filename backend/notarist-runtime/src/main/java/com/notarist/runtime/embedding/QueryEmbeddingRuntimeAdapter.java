package com.notarist.runtime.embedding;

import com.notarist.runtime.provider.registry.RuntimeRegistry;
import com.notarist.search.application.port.out.QueryEmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real QueryEmbeddingPort implementation — delegates to the registry-selected EmbeddingProvider
 * (EMBED_PROVIDER: ollama | sidecar) for query-time embedding generation.
 *
 * Dependency: notarist-runtime depends on notarist-search for QueryEmbeddingPort.
 * Wire: Spring auto-wires this bean wherever QueryEmbeddingPort is injected
 * (mirrors QdrantSearchAdapter in notarist-infra / RegistryLlmPort here).
 *
 * The active provider throws EmbeddingRuntimeException on failure (degraded service, queue
 * saturation, timeout, dimension mismatch); this adapter lets that propagate — SemanticRetriever is
 * responsible for catching it and degrading gracefully to zero semantic hits rather than failing
 * the whole search.
 */
@Component
public class QueryEmbeddingRuntimeAdapter implements QueryEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingRuntimeAdapter.class);

    private final RuntimeRegistry providerRegistry;

    public QueryEmbeddingRuntimeAdapter(RuntimeRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @Override
    public float[] embedQuery(String text, String traceId) {
        log.debug("QueryEmbeddingRuntimeAdapter: embedding query traceId={}", traceId);
        return providerRegistry.embedding().embed(text, traceId);
    }
}
