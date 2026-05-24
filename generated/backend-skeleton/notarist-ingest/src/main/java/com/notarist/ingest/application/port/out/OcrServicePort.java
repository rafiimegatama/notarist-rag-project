package com.notarist.ingest.application.port.out;

/** Port for PaddleOCR sidecar — :8081. Adapter enforces 120s timeout with 3x retry. */
public interface OcrServicePort {

    OcrResult extractText(String minioObjectKey, OcrConfig config);

    record OcrConfig(int dpi, String language, boolean enhanceContrast) {
        public static OcrConfig defaultIndonesia() {
            return new OcrConfig(300, "id", true);
        }
    }

    record OcrResult(
        String ocrObjectKey,
        int pageCount,
        int extractedTextLength,
        float confidenceAvg,
        java.util.List<String> warnings,
        long processingMs
    ) {}
}
