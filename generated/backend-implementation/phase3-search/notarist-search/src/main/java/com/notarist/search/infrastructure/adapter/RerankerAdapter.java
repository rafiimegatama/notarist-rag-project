package com.notarist.search.infrastructure.adapter;

import com.notarist.search.application.port.out.RerankerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cross-encoder reranker stub — Phase 3.
 * Returns fixed score 0.5 for every candidate (identity passthrough).
 *
 * PHASE 6A.2-FIX: @Component REMOVED.
 * Production bean: RerankerRuntimeWorker in notarist-runtime.
 * Test usage: declare as @Bean in @TestConfiguration.
 */
public class RerankerAdapter implements RerankerPort {

    private static final Logger log = LoggerFactory.getLogger(RerankerAdapter.class);
    private static final double STUB_SCORE = 0.5;

    @Override
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates) {
        log.debug("RerankerAdapter stub — passthrough {} candidates", candidates.size());
        return candidates.stream()
                .map(c -> new RerankResult(c.chunkId(), STUB_SCORE))
                .collect(Collectors.toList());
    }
}
