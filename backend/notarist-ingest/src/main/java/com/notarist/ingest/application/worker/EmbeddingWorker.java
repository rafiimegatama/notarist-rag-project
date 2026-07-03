package com.notarist.ingest.application.worker;

import com.notarist.ingest.application.port.out.EmbeddingPort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stub embedding worker — calls EmbeddingPort (adapter stub) to produce bge-m3 vectors.
 * Real bge-m3 invocation via Ollama in Phase 2C when real chunk text is stored.
 */
@Service
public class EmbeddingWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

    private final EmbeddingPort embeddingPort;

    public EmbeddingWorker(EmbeddingPort embeddingPort) {
        this.embeddingPort = embeddingPort;
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.EMBED_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        log.info("Embedding worker starting: ingestionId={}", context.ingestionId());

        List<EmbeddingPort.ChunkInput> inputs = buildChunkInputs(job);
        if (inputs.isEmpty()) {
            throw IngestionStageException.fatal(
                    "INGEST_EMBED_NO_CHUNKS", PipelineStatus.EMBED_PENDING,
                    "No chunks found to embed for ingestionId=" + context.ingestionId());
        }

        List<EmbeddingPort.EmbeddingResult> results;
        try {
            results = embeddingPort.generateEmbeddings(inputs, EmbeddingPort.EmbeddingConfig.defaultBgeM3());
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_EMBED_SIDECAR_ERROR", PipelineStatus.EMBED_PENDING,
                    "Embedding sidecar failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        if (results.isEmpty()) {
            throw IngestionStageException.retryable(
                    "INGEST_EMBED_EMPTY_RESULT", PipelineStatus.EMBED_PENDING,
                    "Embedding produced no results for ingestionId=" + context.ingestionId());
        }

        log.info("Embedding completed: ingestionId={} vectors={}", context.ingestionId(), results.size());
    }

    private List<EmbeddingPort.ChunkInput> buildChunkInputs(IngestionJob job) {
        return List.of(new EmbeddingPort.ChunkInput(
                job.getDocumentId().value().toString(),
                "[STUB] text for ingestionId=" + job.getIngestionId()
        ));
    }
}
