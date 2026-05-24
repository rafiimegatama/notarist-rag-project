package com.notarist.ingest.application.worker;

import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Produces ChunkMetadata records using document-type-aware boundary strategy.
 * Actual semantic chunking is stub — no real NLP boundary detection yet.
 * Real implementation in Phase 2C when real text content is available.
 */
@Service
public class ChunkWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(ChunkWorker.class);

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.CHUNK_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        log.info("Chunk worker starting: ingestionId={} documentType={}",
                context.ingestionId(), job.getDocumentType());

        int[] chunkParams = resolveChunkParams(job);
        int targetTokens = chunkParams[0];
        int overlapTokens = chunkParams[1];
        String strategy = job.getDocumentType().name();

        List<ChunkMetadata> chunks = produceChunkMetadata(job, strategy, targetTokens, overlapTokens);

        if (chunks.isEmpty()) {
            throw IngestionStageException.fatal(
                    "INGEST_CHUNK_EMPTY_RESULT", PipelineStatus.CHUNK_PENDING,
                    "Chunking produced zero chunks for ingestionId=" + context.ingestionId());
        }

        log.info("Chunking completed: ingestionId={} strategy={} chunks={} avgTokens={}",
                context.ingestionId(), strategy, chunks.size(), targetTokens);
    }

    private int[] resolveChunkParams(IngestionJob job) {
        return switch (job.getDocumentType()) {
            case AKTA -> new int[]{
                    (NotaristConstants.AKTA_CHUNK_MAX_TOKENS + NotaristConstants.AKTA_CHUNK_MIN_TOKENS) / 2,
                    (int) (NotaristConstants.AKTA_CHUNK_MAX_TOKENS * NotaristConstants.AKTA_CHUNK_OVERLAP_PCT / 100.0)
            };
            case REGULASI -> new int[]{
                    (NotaristConstants.REGULASI_CHUNK_MAX_TOKENS + NotaristConstants.REGULASI_CHUNK_MIN_TOKENS) / 2,
                    0
            };
            case SOP -> new int[]{
                    (NotaristConstants.SOP_CHUNK_MAX_TOKENS + NotaristConstants.SOP_CHUNK_MIN_TOKENS) / 2,
                    (int) (NotaristConstants.SOP_CHUNK_MAX_TOKENS * NotaristConstants.SOP_CHUNK_OVERLAP_PCT / 100.0)
            };
        };
    }

    private List<ChunkMetadata> produceChunkMetadata(
            IngestionJob job, String strategy, int targetTokens, int overlapTokens) {
        List<ChunkMetadata> chunks = new ArrayList<>();
        int estimatedChunks = 5;

        for (int i = 0; i < estimatedChunks; i++) {
            int startOffset = i * (targetTokens - overlapTokens);
            ChunkMetadata chunk = new ChunkMetadata(
                    new ChunkId(UUID.randomUUID()),
                    job.getIngestionId(),
                    job.getDocumentId(),
                    i,
                    startOffset,
                    startOffset + targetTokens,
                    targetTokens,
                    strategy,
                    overlapTokens,
                    null,
                    null,
                    null,
                    "notarist-ocr/" + job.getTenantId() + "/" + job.getDocumentId().value() + ".txt",
                    null
            );
            chunks.add(chunk);
        }
        return chunks;
    }
}
