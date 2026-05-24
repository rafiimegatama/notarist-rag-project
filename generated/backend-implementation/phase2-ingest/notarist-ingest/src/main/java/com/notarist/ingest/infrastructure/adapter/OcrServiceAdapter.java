package com.notarist.ingest.infrastructure.adapter;

import com.notarist.ingest.application.port.out.OcrServicePort;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Stub adapter for PaddleOCR sidecar on :8081.
 * Returns a deterministic stub result. Replace with real HTTP call in Phase 2C.
 */
@Component
public class OcrServiceAdapter implements OcrServicePort {

    private static final Logger log = LoggerFactory.getLogger(OcrServiceAdapter.class);

    private final String sidecarUrl;

    public OcrServiceAdapter(
            @Value("${notarist.ingest.ocr.sidecar-url:http://localhost:8081}") String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
    }

    @Override
    public OcrResult extractText(String minioObjectKey, OcrConfig config) {
        log.info("[STUB] OCR extractText objectKey={} sidecar={}", minioObjectKey, sidecarUrl);

        return new OcrResult(
                minioObjectKey.replace("notarist-raw/", "notarist-ocr/") + ".txt",
                5,
                2048,
                0.92f,
                List.of(),
                250L
        );
    }
}
