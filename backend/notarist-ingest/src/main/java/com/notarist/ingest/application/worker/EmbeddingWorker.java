package com.notarist.ingest.application.worker;

import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.application.port.out.ChunkMetadataRepository;
import com.notarist.ingest.application.port.out.EmbeddingPort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Embeds the real chunk text persisted by the CHUNK stage.
 *
 * Loads unembedded chunks from chunk_index, sends their text to the bge-m3
 * embedding service in bounded batches, and persists each vector back to the
 * chunk row. Retry-safe: chunks embedded in a previous partial run carry a
 * non-null embedding and are excluded from the work set, and each batch is
 * persisted as it completes so a mid-run failure loses at most one batch.
 */
@Service
public class EmbeddingWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

    private final ChunkMetadataRepository chunkRepository;
    private final EmbeddingPort embeddingPort;
    private final int batchSize;

    public EmbeddingWorker(
            ChunkMetadataRepository chunkRepository,
            EmbeddingPort embeddingPort,
            @Value("${notarist.ingest.embedding.batch-size:16}") int batchSize) {
        this.chunkRepository = chunkRepository;
        this.embeddingPort = embeddingPort;
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.EMBED_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        log.info("Embedding worker starting: ingestionId={}", context.ingestionId());

        List<ChunkMetadata> pending = chunkRepository.findUnembedded(job.getIngestionId());
        if (pending.isEmpty()) {
            List<ChunkMetadata> all = chunkRepository.findByIngestionId(job.getIngestionId());
            if (all.isEmpty()) {
                throw IngestionStageException.fatal(
                        "INGEST_EMBED_NO_CHUNKS", PipelineStatus.EMBED_PENDING,
                        "No chunks found to embed for ingestionId=" + context.ingestionId()
                        + " — CHUNK stage did not persist its output");
            }
            log.info("Embedding already complete for ingestionId={} ({} chunks) — idempotent no-op",
                    context.ingestionId(), all.size());
            return;
        }

        EmbeddingPort.EmbeddingConfig config = EmbeddingPort.EmbeddingConfig.defaultBgeM3();
        int embedded = 0;

        for (int from = 0; from < pending.size(); from += batchSize) {
            List<ChunkMetadata> batch = pending.subList(from, Math.min(from + batchSize, pending.size()));
            embedBatchAndPersist(batch, config, context);
            embedded += batch.size();
        }

        log.info("Embedding completed: ingestionId={} vectors={}", context.ingestionId(), embedded);
    }

    private void embedBatchAndPersist(
            List<ChunkMetadata> batch,
            EmbeddingPort.EmbeddingConfig config,
            WorkerContext context) throws IngestionStageException {

        List<EmbeddingPort.ChunkInput> inputs = batch.stream()
                .map(c -> new EmbeddingPort.ChunkInput(c.chunkId().value().toString(), c.chunkText()))
                .toList();

        List<EmbeddingPort.EmbeddingResult> results;
        try {
            results = embeddingPort.generateEmbeddings(inputs, config);
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_EMBED_SIDECAR_ERROR", PipelineStatus.EMBED_PENDING,
                    "Embedding sidecar failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        if (results.size() != batch.size()) {
            throw IngestionStageException.retryable(
                    "INGEST_EMBED_EMPTY_RESULT", PipelineStatus.EMBED_PENDING,
                    "Embedding returned " + results.size() + " vectors for " + batch.size()
                    + " chunks, ingestionId=" + context.ingestionId());
        }

        Map<String, float[]> vectorsByChunkId = results.stream()
                .collect(Collectors.toMap(EmbeddingPort.EmbeddingResult::chunkId,
                                          EmbeddingPort.EmbeddingResult::vector));

        Instant embeddedAt = Instant.now();
        List<ChunkMetadata> embeddedChunks = new ArrayList<>(batch.size());
        for (ChunkMetadata chunk : batch) {
            float[] vector = vectorsByChunkId.get(chunk.chunkId().value().toString());
            if (vector == null || vector.length != NotaristConstants.EMBEDDING_DIMENSION) {
                throw IngestionStageException.retryable(
                        "INGEST_EMBED_INVALID_VECTOR", PipelineStatus.EMBED_PENDING,
                        "Missing or wrong-dimension vector for chunkId=" + chunk.chunkId().value()
                        + " ingestionId=" + context.ingestionId());
            }
            embeddedChunks.add(chunk.withEmbedding(vector, config.model(), embeddedAt));
        }

        chunkRepository.saveEmbeddings(embeddedChunks);
    }
}
