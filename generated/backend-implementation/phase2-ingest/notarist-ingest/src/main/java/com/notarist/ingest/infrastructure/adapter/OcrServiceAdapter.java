package com.notarist.ingest.infrastructure.adapter;

import com.notarist.core.domain.ocr.OcrConfig;
import com.notarist.core.domain.ocr.OcrResult;
import com.notarist.core.port.ocr.OcrPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub implementation of OcrPort for ingest pipeline testing.
 *
 * PHASE 6A.2-FIX:
 *   - Migrated from OcrServicePort to core.OcrPort
 *   - @Component REMOVED: PaddleOcrAdapter in notarist-runtime is the production bean
 *   - For test-only wiring: declare as @Bean in a @TestConfiguration class
 *
 * Boundary: notarist-ingest depends on notarist-core (OcrPort, OcrConfig, OcrResult).
 * No dependency on notarist-runtime needed here.
 */
public class OcrServiceAdapter implements OcrPort {

    private static final Logger log = LoggerFactory.getLogger(OcrServiceAdapter.class);

    @Override
    public OcrResult extractText(String minioObjectKey, OcrConfig config) {
        log.info("[STUB] OCR extractText objectKey={} lang={}", minioObjectKey, config.language());

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
