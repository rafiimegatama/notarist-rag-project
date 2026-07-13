package com.notarist.ingest.application.worker;

import com.notarist.core.domain.policy.OcrConfidencePolicy;
import com.notarist.core.domain.policy.OcrReviewStatus;
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

        if (result.extractedTextLength() == 0) {
            throw IngestionStageException.fatal(
                    "INGEST_OCR_EMPTY_RESULT", PipelineStatus.OCR_PENDING,
                    "OCR produced no text for ingestionId=" + context.ingestionId());
        }

        OcrReviewStatus reviewStatus = OcrConfidencePolicy.evaluate(result.confidenceAvg());
        if (reviewStatus == OcrReviewStatus.REJECTED) {
            throw IngestionStageException.fatal(
                    "INGEST_OCR_REJECTED_LOW_CONFIDENCE", PipelineStatus.OCR_PENDING,
                    "OCR confidence " + result.confidenceAvg() + " below rejection threshold "
                    + OcrConfidencePolicy.REVIEW_THRESHOLD + " for ingestionId=" + context.ingestionId()
                    + " — document must be re-scanned");
        }
        if (reviewStatus == OcrReviewStatus.LOW_CONFIDENCE_REVIEW) {
            log.warn("Low OCR confidence {} for ingestionId={} — chunks will be non-searchable pending review",
                    result.confidenceAvg(), context.ingestionId());
        }

        // Persist the OCR output location + confidence on the job. PipelineCoordinator
        // saves the job after process() returns, making this durable for NER/CHUNK stages.
        job.recordOcrResult(result.confidenceAvg(), result.ocrObjectKey());

        log.info("OCR completed: ingestionId={} pages={} chars={} confidence={} ocrObjectKey={}",
                context.ingestionId(), result.pageCount(),
                result.extractedTextLength(), result.confidenceAvg(), result.ocrObjectKey());
    }

    private String buildRawObjectKey(IngestionJob job) {
        return "notarist-raw/" + job.getTenantId() + "/" + job.getDocumentId().value();
    }
}
