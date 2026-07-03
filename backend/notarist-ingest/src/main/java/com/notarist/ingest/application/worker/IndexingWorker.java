package com.notarist.ingest.application.worker;

import com.notarist.ingest.application.port.out.VectorIndexPort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stub indexing worker — calls VectorIndexPort (Qdrant adapter stub) to upsert vectors.
 * Real Qdrant upsert in Phase 2C when real vectors are generated.
 */
@Service
public class IndexingWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(IndexingWorker.class);

    private final VectorIndexPort vectorIndexPort;

    public IndexingWorker(VectorIndexPort vectorIndexPort) {
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

        List<VectorIndexPort.IndexableChunk> chunks = buildIndexableChunks(job);
        if (chunks.isEmpty()) {
            throw IngestionStageException.fatal(
                    "INGEST_INDEX_NO_CHUNKS", PipelineStatus.INDEX_PENDING,
                    "No chunks to index for ingestionId=" + context.ingestionId());
        }

        try {
            vectorIndexPort.upsertChunks(chunks);
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_INDEX_QDRANT_ERROR", PipelineStatus.INDEX_PENDING,
                    "Qdrant upsert failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        log.info("Indexing completed: ingestionId={} chunks={}", context.ingestionId(), chunks.size());
    }

    private List<VectorIndexPort.IndexableChunk> buildIndexableChunks(IngestionJob job) {
        float[] stubVector = new float[1024];
        VectorIndexPort.ChunkPayload payload = new VectorIndexPort.ChunkPayload(
                job.getDocumentType(),
                job.getClassificationLevel(),
                null,
                0,
                "[STUB] indexed text for " + job.getIngestionId(),
                "notarist-chunk/" + job.getTenantId() + "/" + job.getDocumentId().value()
        );
        return List.of(new VectorIndexPort.IndexableChunk(
                job.getDocumentId().value().toString(),
                job.getDocumentId(),
                job.getTenantId(),
                stubVector,
                payload
        ));
    }
}
