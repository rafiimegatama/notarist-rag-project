package com.notarist.ingest.application.worker;

import com.notarist.core.domain.policy.OcrConfidencePolicy;
import com.notarist.core.domain.policy.OcrReviewStatus;
import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.util.NotaristConstants;
import com.notarist.ingest.application.port.out.ChunkMetadataRepository;
import com.notarist.ingest.application.port.out.DocumentStoragePort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.ChunkMetadata;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import com.notarist.ingest.domain.service.DocumentChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chunks the real OCR-extracted text (read from MinIO at the ocrObjectKey the
 * OCR stage persisted on the job) using the document-type-aware token-window
 * strategy, then persists the chunks to chunk_index so the EMBED stage can
 * consume them in a later scheduler execution.
 *
 * Idempotent: re-running this stage replaces any previously persisted chunks
 * for the same ingestion (delete-then-insert in ChunkMetadataRepository).
 */
@Service
public class ChunkWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(ChunkWorker.class);

    private final DocumentStoragePort storagePort;
    private final ChunkMetadataRepository chunkRepository;
    private final long maxTextBytes;

    public ChunkWorker(
            DocumentStoragePort storagePort,
            ChunkMetadataRepository chunkRepository,
            @Value("${notarist.ingest.chunk.max-text-bytes:20971520}") long maxTextBytes) {
        this.storagePort = storagePort;
        this.chunkRepository = chunkRepository;
        this.maxTextBytes = maxTextBytes;
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.CHUNK_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        log.info("Chunk worker starting: ingestionId={} documentType={}",
                context.ingestionId(), job.getDocumentType());

        String ocrObjectKey = job.getOcrObjectKey();
        if (ocrObjectKey == null || ocrObjectKey.isBlank()) {
            throw IngestionStageException.fatal(
                    "INGEST_CHUNK_NO_OCR_RESULT", PipelineStatus.CHUNK_PENDING,
                    "No OCR object key recorded on job for ingestionId=" + context.ingestionId()
                    + " — OCR stage did not persist its output");
        }

        String text = readOcrText(ocrObjectKey, context);

        int[] chunkParams = resolveChunkParams(job);
        int targetTokens = chunkParams[0];
        int overlapTokens = chunkParams[1];
        String strategy = job.getDocumentType().name();

        List<DocumentChunker.TextChunk> textChunks =
                DocumentChunker.chunk(text, targetTokens, overlapTokens);

        if (textChunks.isEmpty()) {
            throw IngestionStageException.fatal(
                    "INGEST_CHUNK_EMPTY_RESULT", PipelineStatus.CHUNK_PENDING,
                    "Chunking produced zero chunks for ingestionId=" + context.ingestionId());
        }

        float ocrConfidence = job.getOcrConfidence() != null ? job.getOcrConfidence() : 0f;
        OcrReviewStatus reviewStatus = OcrConfidencePolicy.evaluate(ocrConfidence);
        boolean searchable = reviewStatus.isSearchable();

        List<ChunkMetadata> chunks = textChunks.stream()
                .map(tc -> new ChunkMetadata(
                        new ChunkId(UUID.randomUUID()),
                        job.getIngestionId(),
                        job.getDocumentId(),
                        job.getTenantId(),
                        job.getDocumentType(),
                        job.getClassificationLevel(),
                        tc.index(),
                        tc.startOffset(),
                        tc.endOffset(),
                        tc.tokenCount(),
                        strategy,
                        overlapTokens,
                        tc.sectionTitle(),
                        tc.pasalRef(),
                        null,
                        tc.text(),
                        ocrObjectKey,
                        ocrConfidence,
                        reviewStatus,
                        searchable,
                        null,
                        null,
                        null,
                        Instant.now()))
                .toList();

        chunkRepository.replaceForIngestion(job.getIngestionId(), chunks);

        log.info("Chunking completed: ingestionId={} strategy={} chunks={} searchable={} reviewStatus={}",
                context.ingestionId(), strategy, chunks.size(), searchable, reviewStatus);
    }

    private String readOcrText(String ocrObjectKey, WorkerContext context) throws IngestionStageException {
        try (InputStream in = storagePort.openObject(ocrObjectKey)) {
            byte[] bytes = in.readNBytes((int) Math.min(maxTextBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxTextBytes) {
                throw IngestionStageException.fatal(
                        "INGEST_CHUNK_TEXT_TOO_LARGE", PipelineStatus.CHUNK_PENDING,
                        "OCR text exceeds " + maxTextBytes + " bytes for ingestionId="
                        + context.ingestionId() + " — refusing to load into memory");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IngestionStageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw IngestionStageException.retryable(
                    "INGEST_CHUNK_OCR_READ_ERROR", PipelineStatus.CHUNK_PENDING,
                    "Failed to read OCR text " + ocrObjectKey + " for ingestionId="
                    + context.ingestionId() + ": " + e.getMessage());
        }
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
}
