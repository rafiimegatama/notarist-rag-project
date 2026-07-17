package com.notarist.ingest.application.worker;

import com.notarist.ingest.application.port.out.NerServicePort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.IngestionJob;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NerWorker implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(NerWorker.class);

    private final NerServicePort nerServicePort;

    public NerWorker(NerServicePort nerServicePort) {
        this.nerServicePort = nerServicePort;
    }

    @Override
    public PipelineStatus handledStatus() {
        return PipelineStatus.NER_PENDING;
    }

    @Override
    @Transactional
    public void process(IngestionJob job, WorkerContext context) throws IngestionStageException {
        String ocrObjectKey = job.getOcrObjectKey();
        if (ocrObjectKey == null || ocrObjectKey.isBlank()) {
            throw IngestionStageException.fatal(
                    "INGEST_NER_NO_OCR_RESULT", PipelineStatus.NER_PENDING,
                    "No OCR object key recorded on job for ingestionId=" + context.ingestionId()
                    + " — OCR stage did not persist its output");
        }

        log.info("NER worker starting: ingestionId={} ocrObjectKey={}", context.ingestionId(), ocrObjectKey);

        NerServicePort.NerResult result;
        try {
            result = nerServicePort.extractEntities(
                    ocrObjectKey,
                    job.getDocumentType(),
                    NerServicePort.NerConfig.defaultConfig());
        } catch (Exception e) {
            throw IngestionStageException.retryable(
                    "INGEST_NER_SIDECAR_ERROR", PipelineStatus.NER_PENDING,
                    "NER sidecar call failed for ingestionId=" + context.ingestionId() + ": " + e.getMessage());
        }

        if (!result.piiRedacted()) {
            throw IngestionStageException.fatal(
                    "INGEST_NER_PII_NOT_REDACTED", PipelineStatus.NER_PENDING,
                    "NER did not confirm PII redaction for ingestionId=" + context.ingestionId() +
                    " — refusing to advance to chunking stage");
        }

        log.info("NER completed: ingestionId={} entities={} piiRedacted={} engine={}",
                context.ingestionId(), result.entitiesExtracted().size(),
                result.piiRedacted(), result.engineUsed());
    }
}
