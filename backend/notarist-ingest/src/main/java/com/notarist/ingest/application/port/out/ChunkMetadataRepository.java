package com.notarist.ingest.application.port.out;

import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionId;

import java.util.List;

/**
 * Port for durable chunk persistence (PostgreSQL chunk_index).
 *
 * This is the handoff surface between pipeline stages:
 *   CHUNK  writes rows via replaceForIngestion (idempotent delete-then-insert)
 *   EMBED  reads findUnembedded, writes vectors back via saveEmbeddings
 *   INDEX  reads findEmbedded and upserts to Qdrant
 * BM25 keyword search reads the same table (searchable = TRUE only).
 */
public interface ChunkMetadataRepository {

    /**
     * Atomically replaces all chunks for an ingestion. Idempotent: a re-run of
     * the CHUNK stage wipes any partial prior state before inserting.
     */
    void replaceForIngestion(IngestionId ingestionId, List<ChunkMetadata> chunks);

    List<ChunkMetadata> findByIngestionId(IngestionId ingestionId);

    /** Chunks not yet embedded — the EMBED stage's work set (retry-safe). */
    List<ChunkMetadata> findUnembedded(IngestionId ingestionId);

    /** Chunks with a persisted vector — the INDEX stage's work set. */
    List<ChunkMetadata> findEmbedded(IngestionId ingestionId);

    /** Persists embedding vector, model, and timestamp for each chunk by chunkId. */
    void saveEmbeddings(List<ChunkMetadata> embeddedChunks);
}
