package com.notarist.runtime.ocr.spi;

import java.util.List;

/**
 * What an engine produced.
 *
 * <p>Deliberately shaped like the business-facing {@code OcrServicePort.OcrResult} so
 * {@code OcrRuntimeService} maps it across with no interpretation — plus {@code providerId}, so a
 * result can always be traced back to the engine that produced it. When Surya and PaddleOCR disagree
 * about the same document, the first question is which one ran, and the answer should not require
 * reading logs.
 *
 * <p>{@code confidenceAvg} is normalised to 0.0–1.0 by the PROVIDER. Engines report confidence on
 * wildly different scales (0–100, 0–1, log-probs); normalising at the edge means
 * {@code OcrConfidencePolicy} keeps one set of thresholds that stay meaningful when the engine
 * changes. Getting this wrong silently re-scales the accept/reject boundary for legal documents.
 */
public record OcrExtractionResult(
        String providerId,
        String ocrObjectKey,
        int pageCount,
        int extractedTextLength,
        float confidenceAvg,
        List<String> warnings,
        long durationMs
) {

    public OcrExtractionResult {
        if (warnings == null) {
            warnings = List.of();
        }
        if (confidenceAvg < 0.0f || confidenceAvg > 1.0f) {
            throw new IllegalArgumentException(
                    "confidenceAvg must be normalised to 0.0-1.0 by the provider, got " + confidenceAvg
                    + " from provider '" + providerId + "'. An un-normalised score silently moves the "
                    + "OcrConfidencePolicy accept/reject threshold.");
        }
    }
}
