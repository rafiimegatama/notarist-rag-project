package com.notarist.runtime.ocr.spi;

import java.util.Set;

/**
 * What an OCR engine can actually do.
 *
 * <p>The runtime interrogates this instead of assuming, so an engine that cannot read handwriting
 * degrades <em>honestly</em> — with a warning naming the capability — rather than silently returning
 * a document with the handwritten annotations missing. On a notarial akta, a silently-dropped
 * handwritten margin note is not a cosmetic loss.
 *
 * <p>This is also the capability matrix: {@code /actuator/health} renders these flags for every
 * registered provider, so "can we switch to Gemini for this tenant?" is answered by reading the
 * health endpoint rather than by reading six adapters.
 *
 * <h2>supportsGpu is not requiresGpu</h2>
 * {@link #supportsGpu()} means "this engine can exploit a GPU if one is present" — it does NOT mean
 * the engine needs one. Nothing in this platform requires a GPU: PaddleOCR runs on CPU, just slower,
 * and every cloud engine has no local device at all. The flag exists so {@code OcrBatchSizer} knows
 * whether a hardware-derived batch size is even meaningful for this engine, not so anything can
 * refuse to start without a card.
 *
 * @param supportsVision      raster images (PNG/JPEG/TIFF), i.e. scans and photographs
 * @param supportsPdf         PDF natively, without the caller rasterising pages first
 * @param supportsHandwriting handwritten text — annotations, margin notes, signatures
 * @param supportsTables      table structure preserved rather than flattened into a text blob
 * @param supportsLayout      reading order, blocks, columns, headings — the structure chunking needs
 * @param supportsBatch       several documents in one call, results positionally aligned
 * @param supportsGpu         can exploit a GPU when present. NOT a requirement. See above.
 * @param maxBatchSize        the engine's own hard ceiling. The sizer takes the MINIMUM of this and
 *                            the hardware/config value, so a batch tuned for a 16GB local GPU cannot
 *                            be fired at a cloud API whose documented limit is 5 images per request.
 * @param supportedLanguages  ISO 639-1 codes. Empty = the engine advertises no list; anything passes.
 */
public record OcrCapabilities(
        boolean supportsVision,
        boolean supportsPdf,
        boolean supportsHandwriting,
        boolean supportsTables,
        boolean supportsLayout,
        boolean supportsBatch,
        boolean supportsGpu,
        int maxBatchSize,
        Set<String> supportedLanguages
) {

    public OcrCapabilities {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be >= 1 (1 means: no batching)");
        }
        if (supportedLanguages == null) {
            supportedLanguages = Set.of();
        }
        if (!supportsBatch && maxBatchSize != 1) {
            throw new IllegalArgumentException(
                    "maxBatchSize must be 1 when supportsBatch is false — an engine that cannot batch "
                    + "must not advertise a ceiling the runtime would then try to fill");
        }
        if (!supportsVision && !supportsPdf) {
            // An engine that accepts neither images nor PDFs cannot OCR anything this system holds.
            throw new IllegalArgumentException(
                    "an OCR provider must support at least one of vision (images) or PDF");
        }
    }

    /**
     * A conservative starting point for a new provider: images + PDF, no batching, nothing fancy,
     * no GPU. Override only the flags you have actually verified against the engine — an
     * unverified {@code true} here is a silent data-quality bug, not a config nicety.
     */
    public static OcrCapabilities basic(Set<String> languages) {
        return new OcrCapabilities(
                true,   // vision
                true,   // pdf
                false,  // handwriting
                false,  // tables
                false,  // layout
                false,  // batch
                false,  // gpu
                1,
                languages);
    }

    /** True when the engine advertises a language list and this one is not on it. */
    public boolean rejectsLanguage(String language) {
        return !supportedLanguages.isEmpty()
                && language != null
                && !supportedLanguages.contains(language);
    }

    /** Compact one-line rendering for the health endpoint's capability matrix. */
    public String toMatrixString() {
        return "vision=" + supportsVision
                + " pdf=" + supportsPdf
                + " handwriting=" + supportsHandwriting
                + " tables=" + supportsTables
                + " layout=" + supportsLayout
                + " batch=" + supportsBatch + "(max=" + maxBatchSize + ")"
                + " gpu=" + supportsGpu
                + " langs=" + (supportedLanguages.isEmpty() ? "any" : supportedLanguages);
    }
}
