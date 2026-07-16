package com.notarist.runtime.ocr.spi;

/**
 * One document to OCR.
 *
 * <p>The document is passed BY REFERENCE — an object key, not bytes. The engine fetches the file
 * from object storage itself. That keeps multi-hundred-megabyte akta PDFs out of the JVM heap and
 * off the wire twice, and it is the contract the current sidecar already speaks.
 *
 * @param sourceObjectKey        storage key of the source document. Named for what it IS, not for
 *                               which storage backend happens to hold it.
 * @param language               ISO 639-1 code, e.g. "id".
 * @param enableHandwriting      request handwriting recognition. Providers that cannot do it say so
 *                               via {@link OcrCapabilities}; the runtime warns rather than pretending.
 * @param enableTables           request table-structure preservation.
 * @param minConfidenceThreshold below this, the engine may drop a token. NOT the accept/reject
 *                               decision — that belongs to OcrConfidencePolicy in the domain, and
 *                               stays there.
 * @param batchSizeHint          how many pages the engine may push through the device at once.
 *                               A HINT: the engine is free to ignore it, and it is derived from the
 *                               detected hardware, never from a hardcoded GPU model.
 */
public record OcrExtractionRequest(
        String sourceObjectKey,
        String language,
        boolean enableHandwriting,
        boolean enableTables,
        float minConfidenceThreshold,
        int batchSizeHint
) {

    public OcrExtractionRequest {
        if (sourceObjectKey == null || sourceObjectKey.isBlank()) {
            throw new IllegalArgumentException("sourceObjectKey is required");
        }
        if (batchSizeHint < 1) {
            batchSizeHint = 1;
        }
    }
}
