package com.notarist.runtime.ocr.spi;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-agnostic OCR SPI — the OCR branch of the unified runtime registry, and the sibling of
 * {@code InferenceProvider} (LLM), {@code EmbeddingProvider} and {@code RerankerProvider} in
 * {@code com.notarist.runtime.provider}.
 *
 * <pre>
 *   RuntimeRegistry
 *     ├── OCR Registry        ← OcrProvider        (OCR_PROVIDER)      ← this SPI
 *     ├── LLM Registry        ← InferenceProvider  (LLM_PROVIDER)
 *     ├── Embedding Registry  ← EmbeddingProvider  (EMBED_PROVIDER)
 *     └── Reranker Registry   ← RerankerProvider   (RERANK_PROVIDER)
 * </pre>
 *
 * <p>It follows their contract exactly — {@code id()}, {@code displayName()}, {@code capabilities()},
 * {@code isAvailable()}, {@code health()}, selection from {@code notarist.runtime.<capability>.provider}
 * — so the four capabilities are reasoned about uniformly and OCR is not a special case with its own
 * parallel conventions. Concrete engines (PaddleOCR today; Surya, Gemini, Mistral, Azure, Google
 * Vision later) register as Spring beans and {@code OcrProviderRegistry} resolves the active one.
 *
 * <p><b>Business logic never sees this interface.</b> {@code OcrWorker} depends on
 * {@code OcrServicePort}, whose single implementation ({@code OcrRuntimeService}) routes through the
 * registry. Swapping engines is an env-var change and a restart — never a code change.
 *
 * <p><b>What an implementation must NOT do.</b> Everything cross-cutting is applied once, above, by
 * {@code OcrRuntimeService}: timeout, retry with backoff, degradation, metrics, thread isolation. A
 * provider that adds its own retry loop multiplies against the outer one (3 × 3 = 9 attempts against
 * an engine that is, by hypothesis, already overloaded). A provider does exactly one thing: turn a
 * request into a result, or throw {@link OcrProviderException} saying honestly whether another
 * attempt is worth making.
 */
public interface OcrProvider {

    /**
     * Stable lowercase id used for config selection, e.g. {@code "paddle"}, {@code "surya"},
     * {@code "gemini"}. A plain string, not an enum — an enum would force every new engine to edit a
     * shared file, which is exactly the coupling this SPI exists to remove.
     */
    String id();

    /** Human-readable name for logs and the capability matrix. */
    default String displayName() {
        return id();
    }

    /** What this engine can actually do. The runtime reads this instead of assuming. */
    OcrCapabilities capabilities();

    /**
     * Extract text from one document.
     *
     * @throws OcrProviderException with {@code retryable} set honestly — the retry policy trusts it
     *         completely. A corrupt PDF is permanent; a 503 from an overloaded engine is retryable.
     */
    OcrExtractionResult extract(OcrExtractionRequest request) throws OcrProviderException;

    /**
     * Extract a batch in a single call.
     *
     * <p>The default loops, which is correct but wastes a GPU: the win from batching is amortising
     * model load and keeping the device saturated across documents, and a sequential loop gets none
     * of it. An engine that can genuinely batch overrides this AND reports
     * {@code capabilities().supportsBatch()}; the runtime only routes batches to engines that claim it.
     *
     * <p><b>Contract:</b> the returned list is positionally aligned with {@code requests}. An engine
     * that cannot guarantee that must not claim batch support — misaligned results would attach one
     * akta's OCR text to another akta's record, which is the worst failure this system has.
     */
    default List<OcrExtractionResult> extractBatch(List<OcrExtractionRequest> requests)
            throws OcrProviderException {
        List<OcrExtractionResult> results = new ArrayList<>(requests.size());
        for (OcrExtractionRequest request : requests) {
            results.add(extract(request));
        }
        return results;
    }

    /**
     * Cheap, non-throwing probe for the health endpoint and the startup check.
     *
     * <p>Must NOT throw: a health check that throws takes down the very endpoint that exists to
     * describe the outage. Return {@link OcrProviderHealth#down(String, String)} instead.
     */
    OcrProviderHealth health();

    /**
     * True when this provider is reachable and not degraded.
     *
     * <p>Present to match the sibling SPIs so the registry treats all four capabilities uniformly.
     * Defaults to the health probe rather than letting a provider drift into two disagreeing answers
     * to the same question.
     */
    default boolean isAvailable() {
        try {
            OcrProviderHealth health = health();
            return health != null && health.isUp();
        } catch (Exception e) {
            return false;
        }
    }
}
