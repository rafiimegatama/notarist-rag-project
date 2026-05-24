package com.notarist.search.domain.model;

import com.notarist.core.domain.valueobject.*;

import java.time.LocalDate;
import java.util.UUID;

/** A single retrieval result from the hybrid pipeline. */
public record RetrievalResult(
    ChunkId chunkId,
    DocumentId documentId,
    String documentTitle,
    JenisDokumen documentType,
    JenisAkta jenisAkta,
    NomorAkta nomorAkta,
    String chunkText,
    int chunkIndex,
    int rank,
    RankingScore scores,
    ChunkMetadata metadata
) {
    public record ChunkMetadata(
        LocalDate tanggalAkta,
        UUID notarisId,
        String sectionTitle,
        String pasalRef,
        Integer pageNumber,
        String sourceObjectKey
    ) {}
}
