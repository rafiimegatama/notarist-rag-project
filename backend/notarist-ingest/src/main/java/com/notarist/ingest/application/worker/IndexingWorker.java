package com.notarist.ingest.application.worker;

import com.notarist.ingest.application.port.out.ChunkMetadataRepository;
import com.notarist.ingest.application.port.out.VectorIndexPort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Upserts the real embedded chunks persisted by the EMBED stage into Qdrant.
 *
 * Loads every chunk with a persisted vector for this ingestion and sends the
 * real vector + real chunk text + section/pasal metadata to the vector index.
 * Idempotent: Qdrant upsert is keyed by chunkId, so a re-run overwrites the
 * same points rather than duplicating them.
 */
@Service
public class IndexingWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexingWorker.class);

    private final ChunkMetadataRepository chunkRepository;
    private final VectorIndexPort vectorIndexPort;

    public IndexingWorker(
            ChunkMetadataRepository chunkRepository,
            VectorIndexPort vectorIndexPort) {
        this.chunkRepository = chunkRepository;
        this.vectorIndexPort = vectorIndexPort;
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.INDEX_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        log.info("Indexing worker starting: ingestionId={}", context.ingestionId());

        List<ChunkMetadata> embedded = chunkRepository.findEmbedded(job.getIngestionId());
        if (embedded.isEmpty()) {
            throw IngestionStageException.fatal(
                    "INGEST_INDEX_NO_CHUNKS", PipelineStatus.INDEX_PENDING,
                    "No embedded chunks to index for ingestionId=" + context.ingestionId()
                    + " — EMBED stage did not persist vectors");
        }

        List<VectorIndexPort.IndexableChunk> chunks = embedded.stream()
                .map(this::toIndexableChunk)
                .toList();

        try {
            vectorIndexPort.upsertChunks(chunks);
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_INDEX_QDRANT_ERROR", PipelineStatus.INDEX_PENDING,
                    "Qdrant upsert failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        log.info("Indexing completed: ingestionId={} chunks={}", context.ingestionId(), chunks.size());
    }

    private VectorIndexPort.IndexableChunk toIndexableChunk(ChunkMetadata chunk) {
        VectorIndexPort.ChunkPayload payload = new VectorIndexPort.ChunkPayload(
                chunk.documentType(),
                chunk.classificationLevel(),
                chunk.sectionTitle(),
                chunk.pasalRef(),
                chunk.chunkIndex(),
                chunk.chunkText(),
                chunk.sourceObjectKey(),
                chunk.searchable());
        return new VectorIndexPort.IndexableChunk(
                chunk.chunkId().value().toString(),
                chunk.documentId(),
                chunk.tenantId(),
                chunk.embedding(),
                payload);
    }
}
