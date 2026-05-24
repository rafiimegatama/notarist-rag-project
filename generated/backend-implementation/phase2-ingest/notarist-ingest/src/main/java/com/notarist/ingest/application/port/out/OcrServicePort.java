package com.notarist.ingest.application.port.out;

/**
 * @deprecated PHASE 6A.2-FIX — OCR contract unified under core.OcrPort.
 *
 * This module now uses:
 *   com.notarist.core.port.ocr.OcrPort
 *   com.notarist.core.domain.ocr.OcrConfig
 *   com.notarist.core.domain.ocr.OcrResult
 *
 * OcrWorker has been migrated to inject OcrPort.
 * OcrServiceAdapter has been migrated to implement OcrPort.
 */
@Deprecated(since = "6A.2-FIX", forRemoval = true)
public interface OcrServicePort {
}
