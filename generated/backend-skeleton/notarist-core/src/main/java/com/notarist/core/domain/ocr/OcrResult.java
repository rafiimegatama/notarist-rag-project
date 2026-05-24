package com.notarist.core.domain.ocr;

import java.util.List;

/**
 * Value object returned by any OcrPort implementation.
 * Canonical shared contract between notarist-ingest and notarist-runtime.
 */
public record OcrResult(
        String ocrObjectKey,
        int pageCount,
        int extractedTextLength,
        float confidenceAvg,
        List<String> warnings,
        long durationMs
) {
    public OcrResult {
        warnings = List.copyOf(warnings != null ? warnings : List.of());
    }
}
