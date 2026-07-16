package com.notarist.runtime.provider;

import com.notarist.search.application.port.out.RerankerPort;

import java.util.List;

/**
 * Provider-agnostic reranking SPI — one implementation per backend (a local cross-encoder today; a
 * hosted reranker or {@code none} passthrough otherwise).
 *
 * <p>Concrete providers register as Spring beans; {@code RerankerRegistry} (part of the unified
 * {@code RuntimeRegistry}) selects the active one from {@code notarist.runtime.reranker.provider}
 * (env {@code RERANK_PROVIDER}). Reuses {@link RerankerPort}'s record types so the
 * {@code RegistryRerankerPort} router is a pure pass-through and the search pipeline is unchanged.
 *
 * <p>{@code RERANK_PROVIDER=none} is a first-class choice — {@code NoneRerankerProvider} returns
 * candidates in their original order, correct when no reranker is deployed.
 */
public interface RerankerProvider extends RuntimeProvider {

    /** Reranks candidates for the query. Implementations degrade gracefully rather than throwing. */
    List<RerankerPort.RerankResult> rerank(String query, List<RerankerPort.RerankCandidate> candidates);
}
