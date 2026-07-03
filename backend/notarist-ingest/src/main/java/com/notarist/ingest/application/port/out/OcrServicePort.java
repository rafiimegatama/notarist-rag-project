package com.notarist.ingest.application.port.out;

import java.util.List;

/**
 * Output port for OCR text extraction.
 * Implemented by OcrServiceAdapter (PaddleOCR sidecar stub in Phase 2).
 * Real PaddleOCR HTTP invocation deferred to Phase 2C.
 */
public interface OcrServicePort {

    OcrResult extractText(String minioObjectKey, OcrConfig config);

    record OcrResult(
            String ocrObjectKey,
            int pageCount,
            int extractedTextLength,
            float confidenceAvg,
            List<String> warnings,
            long durationMs
    ) {}

    record OcrConfig(
            String language,
            boolean enableHandwriting,
            boolean enableTables,
            float minConfidenceThreshold
    ) {
        public static OcrConfig defaultIndonesia() {
            return new OcrConfig("id", false, false, 0.4f);
        }
    }
}
