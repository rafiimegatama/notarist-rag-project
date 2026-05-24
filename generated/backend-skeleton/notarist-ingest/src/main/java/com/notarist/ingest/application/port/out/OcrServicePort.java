package com.notarist.ingest.application.port.out;

/**
 * @deprecated PHASE 6A.2-FIX — OCR contract unified under core.OcrPort.
 *
 * Migration: replace all usages with:
 *   com.notarist.core.port.ocr.OcrPort
 *   com.notarist.core.domain.ocr.OcrConfig
 *   com.notarist.core.domain.ocr.OcrResult
 *
 * OcrServiceAdapter now implements OcrPort (stub).
 * PaddleOcrAdapter in notarist-runtime implements OcrPort (real).
 *
 * This interface is retained as a deprecated stub to avoid breaking
 * any intermediate compilation step. Remove after all callers are migrated.
 */
@Deprecated(since = "6A.2-FIX", forRemoval = true)
public interface OcrServicePort {
}
