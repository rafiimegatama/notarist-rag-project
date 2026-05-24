package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Limits chunk count per document to prevent a single document monopolising context.
 * Preserves score-descending order; processes candidates in ranked order.
 */
@Service
public class DiversityFilterService {

    private static final int MAX_CHUNKS_PER_DOCUMENT = 3;

    public List<RetrievedChunk> filter(List<RetrievedChunk> candidates, int maxResults) {
        Map<String, Integer> countByDocument = new HashMap<>();
        List<RetrievedChunk> diverse = new ArrayList<>();

        for (RetrievedChunk chunk : candidates) {
            if (diverse.size() >= maxResults) break;
            String docKey = chunk.documentId().value().toString();
            int count = countByDocument.getOrDefault(docKey, 0);
            if (count < MAX_CHUNKS_PER_DOCUMENT) {
                diverse.add(chunk);
                countByDocument.put(docKey, count + 1);
            }
        }
        return diverse;
    }
}
