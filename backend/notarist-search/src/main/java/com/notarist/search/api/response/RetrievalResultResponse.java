package com.notarist.search.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrievalResultResponse(
    UUID chunkId,
    UUID documentId,
    String documentTitle,
    String documentType,
    String jenisAkta,
    String nomorAkta,
    String chunkText,
    int chunkIndex,
    int rank,
    ScoreDetail scores,
    ChunkMetadataResponse metadata
) {
    public record ScoreDetail(
        Float bm25,
        Float cosine,
        Float rrfScore,
        Float rerankerScore
    ) {}

    public record ChunkMetadataResponse(
        String tanggalAkta,
        UUID notarisId,
        String sectionTitle,
        String pasalRef,
        Integer pageNumber
    ) {}
}
