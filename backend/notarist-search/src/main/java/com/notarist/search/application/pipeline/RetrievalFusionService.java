package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.RetrievalReason;
import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion — merges keyword and semantic retrieval lists.
 * RRF score = Σ 1 / (k + rank_i) where k=60 (standard constant).
 * Chunks present in both lists accumulate scores from both; sole-list chunks score once.
 */
@Service
public class RetrievalFusionService {

    private static final int RRF_K = 60;

    public List<RetrievedChunk> fuse(
            List<RetrievedChunk> keywordResults,
            List<RetrievedChunk> semanticResults) {

        // chunkId → [keywordScore, semanticScore, accumulatedRrfScore]
        Map<String, double[]> scoreMap  = new LinkedHashMap<>();
        Map<String, RetrievedChunk> ref = new LinkedHashMap<>();

        for (int rank = 0; rank < keywordResults.size(); rank++) {
            RetrievedChunk c = keywordResults.get(rank);
            double rrf = 1.0 / (RRF_K + rank + 1);
            scoreMap.put(c.chunkId(), new double[]{c.keywordScore(), 0.0, rrf});
            ref.putIfAbsent(c.chunkId(), c);
        }

        for (int rank = 0; rank < semanticResults.size(); rank++) {
            RetrievedChunk c = semanticResults.get(rank);
            double rrf = 1.0 / (RRF_K + rank + 1);
            if (scoreMap.containsKey(c.chunkId())) {
                double[] scores = scoreMap.get(c.chunkId());
                scores[1] = c.semanticScore();
                scores[2] += rrf;
            } else {
                scoreMap.put(c.chunkId(), new double[]{0.0, c.semanticScore(), rrf});
                ref.put(c.chunkId(), c);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[2], a.getValue()[2]))
                .map(entry -> {
                    double[] scores = entry.getValue();
                    RetrievedChunk base = ref.get(entry.getKey());
                    Set<RetrievalReason> reasons = resolveReasons(scores[0], scores[1]);
                    return new RetrievedChunk(
                            base.chunkId(), base.documentId(), base.tenantId(),
                            base.documentType(), base.classificationLevel(),
                            base.chunkIndex(), base.sectionTitle(), base.pageNumber(),
                            base.text(), base.sourceObjectKey(),
                            scores[0], scores[1], base.rerankScore(), scores[2],
                            reasons);
                })
                .collect(Collectors.toList());
    }

    private Set<RetrievalReason> resolveReasons(double keywordScore, double semanticScore) {
        Set<RetrievalReason> reasons = new HashSet<>();
        if (keywordScore  > 0) reasons.add(RetrievalReason.KEYWORD_MATCH);
        if (semanticScore > 0) reasons.add(RetrievalReason.SEMANTIC_MATCH);
        if (reasons.isEmpty()) reasons.add(RetrievalReason.KEYWORD_MATCH);
        return reasons;
    }
}
