package com.notarist.ingest.application.worker;

import com.notarist.ingest.application.port.out.OcrServicePort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcrWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(OcrWorker.class);

    private final OcrServicePort ocrServicePort;

    public OcrWorker(OcrServicePort ocrServicePort) {
        this.ocrServicePort = ocrServicePort;
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.OCR_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        String objectKey = buildRawObjectKey(job);

        log.info("OCR worker starting: ingestionId={} objectKey={}", context.ingestionId(), objectKey);

        OcrServicePort.OcrResult result;
        try {
            result = ocrServicePort.extractText(objectKey, OcrServicePort.OcrConfig.defaultIndonesia());
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_OCR_SIDECAR_ERROR", PipelineStatus.OCR_PENDING,
                    "OCR sidecar call failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        if (result.confidenceAvg() < 0.4f) {
            log.warn("Low OCR confidence {} for ingestionId={}",
                    result.confidenceAvg(), context.ingestionId());
        }

        if (result.extractedTextLength() == 0) {
            throw IngestionStageException.fatal(
                    "INGEST_OCR_EMPTY_RESULT", PipelineStatus.OCR_PENDING,
                    "OCR produced no text for ingestionId=" + context.ingestionId());
        }

        log.info("OCR completed: ingestionId={} pages={} chars={} confidence={}",
                context.ingestionId(), result.pageCount(),
                result.extractedTextLength(), result.confidenceAvg());
    }

    private String buildRawObjectKey(IngestionJob job) {
        return "notarist-raw/" + job.getTenantId() + "/" + job.getDocumentId().value();
    }
}
