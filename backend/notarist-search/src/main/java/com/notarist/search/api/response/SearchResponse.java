package com.notarist.search.api.response;

import com.notarist.search.application.query.SearchQuery;
import com.notarist.search.domain.model.AssembledContext;
import com.notarist.search.domain.model.GroundingScore;
import com.notarist.search.domain.model.SearchIntent;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        UUID queryId,
        String status,
        SearchIntent intent,
        String normalizedQuery,
        String contextText,
        GroundingScore.Level groundingLevel,
        float groundingOverallScore,
        List<CitationResponse> citations,
        int retrievedChunkCount,
        int estimatedTokenCount,
        boolean contextTruncated,
        long processingTimeMs,
        String errorMessage
) {
    public static SearchResponse success(
            SearchQuery query,
            SearchIntent intent,
            String normalizedQuery,
            AssembledContext context,
            long processingMs) {

        List<CitationResponse> citationResponses = context.citations().stream()
                .map(CitationResponse::from)
                .toList();

        return new SearchResponse(
                query.queryId(),
                "SUCCESS",
                intent,
                normalizedQuery,
                context.assembledText(),
                context.groundingScore().level(),
                context.groundingScore().overallScore(),
                citationResponses,
                context.contextChunks().size(),
                context.estimatedTokenCount(),
                context.truncated(),
                processingMs,
                null);
    }

    public static SearchResponse error(SearchQuery query, String errorMessage, long processingMs) {
        return new SearchResponse(
                query.queryId(),
                "ERROR",
                null, null, null,
                GroundingScore.Level.UNGROUNDED,
                0f,
                List.of(),
                0, 0, false,
                processingMs,
                errorMessage);
    }
}
