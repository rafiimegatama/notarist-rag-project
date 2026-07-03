package com.notarist.runtime.embedding;

import com.notarist.runtime.model.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks embedding model version alignment with the Qdrant index.
 *
 * If the embedding model changes (new version, different weights), all existing
 * Qdrant vectors become stale — they were produced by a different model and cannot
 * be compared to new query vectors. A full reindex is required.
 *
 * Fields:
 *   embeddingGenerationId — UUID identifying the current embedding model generation
 *   indexGenerationId     — UUID of the last Qdrant index built with this generation
 *   reindexRequired       — true when the two IDs diverge
 *
 * Phase 5B: state is in-memory. Persistent tracking (PostgreSQL) deferred to Phase 5C.
 *
 * Lifecycle:
 *   1. On startup: load embeddingGenerationId from config / model registry checksum
 *   2. Compare with stored indexGenerationId (in-memory default = same as embedding)
 *   3. If model checksum changes between deploys: embeddingGenerationId must be bumped externally
 *   4. On reindex complete: call markReindexComplete() to align the IDs
 */
@Component
public class EmbeddingVersionManager {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingVersionManager.class);

    private final AtomicReference<UUID> embeddingGenerationId;
    private final AtomicReference<UUID> indexGenerationId;
    private final AtomicBoolean         reindexRequired;

    public EmbeddingVersionManager(ModelRegistry modelRegistry) {
        // Initialize from model registry checksum — deterministic UUID from checksum string
        UUID initialGenId = toGenId(modelRegistry.getEmbedding().checksum(),
                                    modelRegistry.getEmbedding().version());
        this.embeddingGenerationId = new AtomicReference<>(initialGenId);
        this.indexGenerationId     = new AtomicReference<>(initialGenId);
        this.reindexRequired       = new AtomicBoolean(false);

        log.info("EmbeddingVersionManager initialized: generationId={} model={} v={}",
                initialGenId, modelRegistry.getEmbedding().modelName(),
                modelRegistry.getEmbedding().version());
    }

    public UUID currentEmbeddingGenerationId() {
        return embeddingGenerationId.get();
    }

    public UUID currentIndexGenerationId() {
        return indexGenerationId.get();
    }

    public boolean requiresReindex() {
        return reindexRequired.get()
                || !embeddingGenerationId.get().equals(indexGenerationId.get());
    }

    /**
     * Call when the embedding model is upgraded to a new version or checksum.
     * This marks all existing index vectors as stale.
     */
    public void onModelUpgraded(String newModelVersion, String newChecksum) {
        UUID newGenId = toGenId(newChecksum, newModelVersion);
        embeddingGenerationId.set(newGenId);
        reindexRequired.set(true);
        log.warn("REINDEX_REQUIRED: embedding model upgraded to v={} genId={}", newModelVersion, newGenId);
    }

    /**
     * Call after a full Qdrant reindex completes successfully.
     * Aligns indexGenerationId with the current embedding generation.
     */
    public void markReindexComplete() {
        UUID currentGen = embeddingGenerationId.get();
        indexGenerationId.set(currentGen);
        reindexRequired.set(false);
        log.info("Reindex complete: indexGenerationId aligned to {}", currentGen);
    }

    public String reindexStatus() {
        return requiresReindex()
                ? "REINDEX_REQUIRED (embeddingGen=" + embeddingGenerationId.get() +
                  " indexGen=" + indexGenerationId.get() + ")"
                : "IN_SYNC (gen=" + embeddingGenerationId.get() + ")";
    }

    private static UUID toGenId(String checksum, String version) {
        long msb = checksum.hashCode() * 31L + version.hashCode();
        return new UUID(msb, msb ^ version.length());
    }
}
