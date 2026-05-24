package com.notarist.core.port.ocr;

import com.notarist.core.domain.ocr.OcrConfig;
import com.notarist.core.domain.ocr.OcrResult;

/**
 * Shared output port for OCR text extraction.
 *
 * Canonical location: notarist-core.
 * Implemented by:
 *   - notarist-runtime: PaddleOcrAdapter (real PaddleOCR HTTP call)
 *   - notarist-ingest: OcrServiceAdapter (stub/fallback for pipeline testing)
 *
 * Using this port in core eliminates the runtime → ingest dependency
 * that would otherwise exist if PaddleOcrAdapter implemented
 * com.notarist.ingest.application.port.out.OcrServicePort directly.
 */
public interface OcrPort {

    OcrResult extractText(String minioObjectKey, OcrConfig config);
}
