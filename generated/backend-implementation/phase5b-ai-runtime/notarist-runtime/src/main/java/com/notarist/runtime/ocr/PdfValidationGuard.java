package com.notarist.runtime.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pre-OCR PDF validation guard.
 *
 * Catches: corrupt PDFs, oversized files, excessive page counts.
 * Called BEFORE submitting to PaddleOcrAdapter so the OCR pool
 * is never saturated by unprocessable documents.
 *
 * Limits enforced for legal domain:
 *   maxFileSizeBytes = 50MB  — covers multi-hundred-page akta PDFs
 *   maxPageCount     = 200   — legal documents rarely exceed this
 *
 * Corruption check: validates %PDF- magic header (first 5 bytes).
 */
@Component
public class PdfValidationGuard {

    private static final Logger log = LoggerFactory.getLogger(PdfValidationGuard.class);

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;  // 50MB
    private static final int  MAX_PAGE_COUNT      = 200;
    private static final byte[] PDF_MAGIC         = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-

    public PdfValidationResult validate(InputStream pdfStream, long fileSizeBytes, String objectKey) {
        // 1. File size check
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            return PdfValidationResult.invalid(
                    "FILE_TOO_LARGE",
                    String.format("File %s exceeds 50MB limit (%d bytes)", objectKey, fileSizeBytes),
                    fileSizeBytes, 0);
        }

        // 2. Magic header check (corruption detection)
        try {
            byte[] header = pdfStream.readNBytes(5);
            if (!matchesMagic(header)) {
                return PdfValidationResult.invalid(
                        "INVALID_PDF_HEADER",
                        "File " + objectKey + " does not start with %PDF- magic bytes",
                        fileSizeBytes, 0);
            }
        } catch (IOException e) {
            log.error("PdfValidationGuard: failed to read header for {}: {}", objectKey, e.getMessage());
            return PdfValidationResult.invalid("READ_ERROR", e.getMessage(), fileSizeBytes, 0);
        }

        // 3. Page count estimation (approximate from fileSize — real count from PaddleOCR response)
        //    We skip a full parse here; PaddleOCR will report actual page count.
        //    Reject only if estimated count far exceeds limit (fileSize / 10KB per page heuristic).
        long estimatedPages = Math.max(1, fileSizeBytes / (10 * 1024));
        if (estimatedPages > MAX_PAGE_COUNT * 2) {
            return PdfValidationResult.invalid(
                    "ESTIMATED_PAGE_COUNT_TOO_HIGH",
                    "Estimated page count " + estimatedPages + " exceeds safe limit",
                    fileSizeBytes, (int) estimatedPages);
        }

        log.debug("PdfValidationGuard: {} OK ({} bytes)", objectKey, fileSizeBytes);
        return PdfValidationResult.valid(fileSizeBytes, (int) Math.min(estimatedPages, MAX_PAGE_COUNT));
    }

    private boolean matchesMagic(byte[] header) {
        if (header.length < PDF_MAGIC.length) return false;
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (header[i] != PDF_MAGIC[i]) return false;
        }
        return true;
    }

    public record PdfValidationResult(
            boolean valid,
            String errorCode,
            String errorMessage,
            long fileSizeBytes,
            int estimatedPageCount
    ) {
        public static PdfValidationResult valid(long size, int pages) {
            return new PdfValidationResult(true, null, null, size, pages);
        }
        public static PdfValidationResult invalid(String code, String message, long size, int pages) {
            return new PdfValidationResult(false, code, message, size, pages);
        }
    }
}
