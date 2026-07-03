package com.notarist.runtime.reranker;

import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.model.ModelProvider;
import com.notarist.runtime.model.ModelRegistry;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import com.notarist.search.application.port.out.RerankerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real cross-encoder HTTP adapter for reranking.
 * Implements RerankerPort from notarist-search.
 *
 * Endpoint: POST /rerank
 * Request:  {"query": "...", "passages": ["...", "..."], "top_k": N}
 * Response: {"results": [{"index": 0, "score": 0.92}, ...]}
 *
 * Isolated in RerankerQueueIsolation (core=2/max=4).
 * Timeout: from IntegrationTimeouts (15s for reranker).
 * On failure: degradation marked; returns original order with score=0.5 (graceful degradation).
 * CallerRunsPolicy applies backpressure to the calling thread.
 */
@Component
public class RerankerRuntimeWorker implements RerankerPort {

    private static final Logger log = LoggerFactory.getLogger(RerankerRuntimeWorker.class);
    private static final long   RERANK_TIMEOUT_MS   = 15_000L;
    private static final float  DEGRADED_SCORE      = 0.5f;
    private static final int    DEFAULT_TOP_K       = 5;

    private record RankedCandidate(String chunkId, String text, float score) {}

    private final RestTemplate                    restTemplate;
    private final ModelRegistry                   modelRegistry;
    private final RuntimeMetricsRegistry          metrics;
    private final RuntimeDegradationManager       degradation;
    private final TimeoutCancellationOrchestrator timeout;
    private final RerankerQueueIsolation          queue;

    public RerankerRuntimeWorker(
            @Qualifier("aiRuntimeRestTemplate") RestTemplate restTemplate,
            ModelRegistry modelRegistry,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation,
            TimeoutCancellationOrchestrator timeout,
            RerankerQueueIsolation queue) {
        this.restTemplate  = restTemplate;
        this.modelRegistry = modelRegistry;
        this.metrics       = metrics;
        this.degradation   = degradation;
        this.timeout       = timeout;
        this.queue         = queue;
    }

    @Override
    public List<RerankerPort.RerankResult> rerank(String query, List<RerankerPort.RerankCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.RERANKER)) {
            log.warn("RerankerRuntimeWorker: RERANKER degraded — returning passthrough scores");
            return passthroughRank(candidates);
        }

        String rerankId = "rerank-" + System.currentTimeMillis();
        long startMs    = System.currentTimeMillis();

        try {
            List<RankedCandidate> result = queue.submit(() ->
                    timeout.submitWithTimeout(rerankId, () -> callReranker(query, candidates, DEFAULT_TOP_K), RERANK_TIMEOUT_MS)
            ).get();

            long durationMs = System.currentTimeMillis() - startMs;
            metrics.recordInferenceLatency(ModelProvider.CROSS_ENCODER, durationMs);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.RERANKER, false, null);

            log.debug("RerankerRuntimeWorker: reranked {} → {} topK={} durationMs={}",
                    candidates.size(), result.size(), DEFAULT_TOP_K, durationMs);
            return result.stream()
                    .map(r -> new RerankerPort.RerankResult(r.chunkId(), (double) r.score()))
                    .toList();

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordTimeout(ModelProvider.CROSS_ENCODER);
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.RERANKER, true, "timeout");
            log.error("RerankerRuntimeWorker: timeout rerankId={}", rerankId);
            return passthroughRank(candidates);

        } catch (Exception e) {
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.RERANKER, true, e.getMessage());
            log.error("RerankerRuntimeWorker: failed rerankId={}: {}", rerankId, e.getMessage(), e);
            return passthroughRank(candidates);
        }
    }

    @SuppressWarnings("unchecked")
    private List<RankedCandidate> callReranker(String query, List<RerankerPort.RerankCandidate> candidates, int topK) throws Exception {
        String endpoint = modelRegistry.getReranker().endpointUrl() + "/rerank";

        List<String> passages = candidates.stream().map(RerankerPort.RerankCandidate::text).toList();
        Map<String, Object> body = Map.of("query", query, "passages", passages, "top_k", topK);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null) return List.of();

        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        if (results == null) return List.of();

        List<RankedCandidate> ranked = new ArrayList<>();
        for (Map<String, Object> item : results) {
            int   idx   = ((Number) item.get("index")).intValue();
            float score = ((Number) item.get("score")).floatValue();
            if (idx >= 0 && idx < candidates.size()) {
                RerankerPort.RerankCandidate candidate = candidates.get(idx);
                ranked.add(new RankedCandidate(candidate.chunkId(), candidate.text(), score));
            }
        }
        return ranked;
    }

    private List<RerankerPort.RerankResult> passthroughRank(List<RerankerPort.RerankCandidate> candidates) {
        int limit = Math.min(DEFAULT_TOP_K, candidates.size());
        return candidates.subList(0, limit).stream()
                .map(c -> new RerankerPort.RerankResult(c.chunkId(), (double) DEGRADED_SCORE))
                .toList();
    }
}
