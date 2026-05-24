package com.notarist.search.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Composite ranking scores for a retrieval result. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingScore(
    Float bm25,
    Float cosine,
    Float rrfScore,
    Float rerankerScore
) {
    public static RankingScore keywordOnly(float bm25) {
        return new RankingScore(bm25, null, null, null);
    }

    public static RankingScore semanticOnly(float cosine) {
        return new RankingScore(null, cosine, null, null);
    }

    public static RankingScore fused(float bm25, float cosine, float rrfScore) {
        return new RankingScore(bm25, cosine, rrfScore, null);
    }

    public RankingScore withRerankerScore(float rerankerScore) {
        return new RankingScore(bm25, cosine, rrfScore, rerankerScore);
    }
}
