package com.notarist.core.domain.ocr;

/**
 * Configuration for an OCR extraction request.
 * Canonical shared contract between notarist-ingest and notarist-runtime.
 */
public record OcrConfig(
        String language,
        boolean enableHandwriting,
        boolean enableTables,
        float minConfidenceThreshold
) {
    public static OcrConfig defaultIndonesia() {
        return new OcrConfig("id", false, false, 0.4f);
    }
}
