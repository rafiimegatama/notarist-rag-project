package com.notarist.search.application.pipeline;

import com.notarist.search.application.port.out.RerankerPort;
import com.notarist.search.domain.model.RetrievalReason;
import com.notarist.search.domain.model.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delegates reranking to RerankerPort (cross-encoder stub in Phase 3).
 * Gracefully degrades to pre-rerank order if reranker is unavailable.
 */
@Service
public class RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);

    private final RerankerPort rerankerPort;

    public RerankerService(RerankerPort rerankerPort) {
        this.rerankerPort = rerankerPort;
    }

    public List<RetrievedChunk> rerank(String normalizedQuery, List<RetrievedChunk> candidates) {
        if (candidates.isEmpty()) return candidates;

        List<RerankerPort.RerankCandidate> rerankInput = candidates.stream()
                .map(c -> new RerankerPort.RerankCandidate(c.chunkId(), c.text()))
                .collect(Collectors.toList());

        List<RerankerPort.RerankResult> results;
        try {
            results = rerankerPort.rerank(normalizedQuery, rerankInput);
        } catch (Exception e) {
            log.warn("Reranker unavailable, using fused order: {}", e.getMessage());
            return candidates;
        }

        Map<String, Double> rerankScores = results.stream()
                .collect(Collectors.toMap(
                        RerankerPort.RerankResult::chunkId,
                        RerankerPort.RerankResult::rerankScore));

        return candidates.stream()
                .map(chunk -> {
                    double score = rerankScores.getOrDefault(chunk.chunkId(), chunk.fusedScore());
                    RetrievedChunk reranked = chunk.withRerankScore(score);
                    if (score > chunk.fusedScore()) {
                        reranked = reranked.withAdditionalReason(RetrievalReason.RERANK_BOOST);
                    }
                    return reranked;
                })
                .sorted(Comparator.comparingDouble(RetrievedChunk::effectiveScore).reversed())
                .collect(Collectors.toList());
    }
}
