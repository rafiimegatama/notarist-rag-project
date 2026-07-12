package com.notarist.runtime.embedding;

import com.notarist.search.application.port.out.QueryEmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Real QueryEmbeddingPort implementation — delegates to EmbeddingRuntimeWorker
 * (bge-m3 HTTP adapter) for query-time embedding generation.
 *
 * Dependency: notarist-runtime depends on notarist-search for QueryEmbeddingPort.
 * Wire: Spring auto-wires this bean wherever QueryEmbeddingPort is injected
 * (mirrors QdrantSearchAdapter in notarist-infra / OllamaRuntimeAdapter here).
 *
 * EmbeddingRuntimeWorker.embed(...) throws EmbeddingRuntimeException on failure
 * (degraded service, queue saturation, timeout, dimension mismatch); this adapter
 * lets that propagate — SemanticRetriever is responsible for catching it and
 * degrading gracefully to zero semantic hits rather than failing the whole search.
 */
@Component
public class QueryEmbeddingRuntimeAdapter implements QueryEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingRuntimeAdapter.class);

    private final EmbeddingRuntimeWorker embeddingRuntimeWorker;

    public QueryEmbeddingRuntimeAdapter(EmbeddingRuntimeWorker embeddingRuntimeWorker) {
        this.embeddingRuntimeWorker = embeddingRuntimeWorker;
    }

    @Override
    public float[] embedQuery(String text, String traceId) {
        log.debug("QueryEmbeddingRuntimeAdapter: embedding query traceId={}", traceId);
        return embeddingRuntimeWorker.embed(text, traceId);
    }
}
