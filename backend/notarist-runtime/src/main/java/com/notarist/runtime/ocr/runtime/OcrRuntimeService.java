package com.notarist.runtime.ocr.runtime;

import com.notarist.ingest.application.port.out.OcrServicePort;
import com.notarist.runtime.degradation.RuntimeDegradationManager;
import com.notarist.runtime.metrics.RuntimeMetricsRegistry;
import com.notarist.runtime.ocr.config.OcrProperties;
import com.notarist.runtime.ocr.registry.OcrProviderRegistry;
import com.notarist.runtime.ocr.spi.OcrCapabilities;
import com.notarist.runtime.ocr.spi.OcrExtractionRequest;
import com.notarist.runtime.ocr.spi.OcrExtractionResult;
import com.notarist.runtime.ocr.spi.OcrProvider;
import com.notarist.runtime.ocr.spi.OcrProviderException;
import com.notarist.runtime.timeout.TimeoutCancellationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONLY implementation of {@link OcrServicePort}, and the only thing in the system that knows
 * more than one OCR engine could exist.
 *
 * <pre>
 *   OcrWorker  →  OcrServicePort  →  OcrRuntimeService  →  OcrProviderRegistry  →  OcrProvider
 *   (business)     (the port)         (this: cross-cutting)   (selection)            (the engine)
 * </pre>
 *
 * <p>{@code OcrServicePort} is untouched — same signature, same records, same semantics — so
 * {@code OcrWorker} and everything upstream of it cannot tell that OCR became pluggable. That is the
 * point of the exercise: swapping PaddleOCR for Gemini is a config change, not a code change.
 *
 * <p>Everything cross-cutting is applied here, exactly once, for every engine:
 * <ul>
 *   <li><b>degradation</b> — short-circuit when OCR is already known-down, so a dead engine does not
 *       eat a timeout budget per document;</li>
 *   <li><b>timeout</b> — a hard wall-clock ceiling, configurable, no longer a {@code 30_000L}
 *       literal buried in the adapter;</li>
 *   <li><b>retry</b> — backoff with jitter, and only on failures the engine called retryable;</li>
 *   <li><b>metrics</b> — latency and timeouts, tagged by the ACTIVE provider id;</li>
 *   <li><b>batch sizing</b> — derived from detected hardware, clamped by the engine's own ceiling.</li>
 * </ul>
 * A provider that re-implements any of these is fighting the runtime, not helping it.
 */
@Component
public class OcrRuntimeService implements OcrServicePort {

    private static final Logger log = LoggerFactory.getLogger(OcrRuntimeService.class);

    private final OcrProviderRegistry registry;
    private final OcrRetryPolicy retryPolicy;
    private final OcrBatchSizer batchSizer;
    private final OcrProperties properties;
    private final TimeoutCancellationOrchestrator timeout;
    private final RuntimeMetricsRegistry metrics;
    private final RuntimeDegradationManager degradation;

    public OcrRuntimeService(
            OcrProviderRegistry registry,
            OcrRetryPolicy retryPolicy,
            OcrBatchSizer batchSizer,
            OcrProperties properties,
            TimeoutCancellationOrchestrator timeout,
            RuntimeMetricsRegistry metrics,
            RuntimeDegradationManager degradation) {
        this.registry = registry;
        this.retryPolicy = retryPolicy;
        this.batchSizer = batchSizer;
        this.properties = properties;
        this.timeout = timeout;
        this.metrics = metrics;
        this.degradation = degradation;
    }

    @Override
    public OcrResult extractText(String sourceObjectKey, OcrConfig config) {
        OcrProvider provider = registry.active();

        if (degradation.isDegraded(RuntimeDegradationManager.AiRuntime.OCR)) {
            // Fail loudly rather than returning the old adapter's empty "degraded result". That
            // result had extractedTextLength == 0, which OcrWorker treats as a FATAL empty-OCR error
            // — so a transient engine outage permanently dead-lettered the document instead of
            // letting it retry. Throwing retryable keeps the job eligible for another attempt.
            throw new OcrProviderException(provider.id(),
                    "OCR runtime is degraded — refusing to call provider '" + provider.id() + "'",
                    true, null);
        }

        warnOnUnsupportedOptions(provider, config);

        OcrExtractionRequest request = toRequest(sourceObjectKey, config);
        long startMs = System.currentTimeMillis();

        try {
            OcrExtractionResult result = retryPolicy.execute("extract:" + sourceObjectKey,
                    () -> timeout.submitWithTimeout(
                            "ocr-" + provider.id() + "-" + sourceObjectKey,
                            () -> provider.extract(request),
                            properties.effectiveTimeoutMs(provider.id())));

            long durationMs = System.currentTimeMillis() - startMs;
            recordSuccess(provider, durationMs);

            log.debug("OCR ok: provider={} key={} pages={} chars={} confidence={} durationMs={}",
                    provider.id(), sourceObjectKey, result.pageCount(),
                    result.extractedTextLength(), result.confidenceAvg(), durationMs);

            return toPortResult(result);

        } catch (TimeoutCancellationOrchestrator.TimeoutException e) {
            metrics.recordCounter("notarist.ocr.timeout", "provider", provider.id());
            degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, true, "timeout");
            log.error("OCR timeout: provider={} key={} after {}ms",
                    provider.id(), sourceObjectKey, properties.effectiveTimeoutMs(provider.id()));
            throw new OcrProviderException(provider.id(),
                    "OCR timed out for: " + sourceObjectKey, true, e);

        } catch (OcrProviderException e) {
            // Only a retryable failure means the ENGINE is unwell. A permanent one means this
            // DOCUMENT is unwell — marking OCR degraded for a corrupt PDF would take the whole
            // pipeline down over one bad file.
            if (e.isRetryable()) {
                degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, true, e.getMessage());
            }
            metrics.recordCounter("notarist.ocr.failure", "provider", provider.id());
            throw e;
        }
    }

    /**
     * Batch extraction. Plumbed through the registry to the engine, clamped by hardware and by the
     * engine's own ceiling.
     *
     * <p><b>Not yet driven by business logic.</b> {@code OcrWorker} pulls one job off the queue at a
     * time and calls {@link #extractText}, so nothing calls this today. Making ingestion batch-aware
     * means changing the worker, which is business logic and out of scope for this module. The
     * capability is here, tested by the type system, and ready for that change — see the runtime
     * checklist.
     */
    public List<OcrResult> extractBatch(List<String> sourceObjectKeys, OcrConfig config) {
        OcrProvider provider = registry.active();
        OcrCapabilities capabilities = provider.capabilities();
        int batchSize = batchSizer.resolveFor(capabilities);

        if (batchSize <= 1) {
            log.debug("OCR batch: provider '{}' batchSize={} — falling back to sequential",
                    provider.id(), batchSize);
            return sourceObjectKeys.stream().map(key -> extractText(key, config)).toList();
        }

        List<OcrResult> results = new ArrayList<>(sourceObjectKeys.size());

        // Chunk to the resolved size: handing the engine the whole list would ignore the very
        // ceiling we just computed.
        for (int i = 0; i < sourceObjectKeys.size(); i += batchSize) {
            final int offset = i;
            List<String> slice = sourceObjectKeys.subList(
                    offset, Math.min(offset + batchSize, sourceObjectKeys.size()));

            List<OcrExtractionRequest> requests = slice.stream()
                    .map(key -> toRequest(key, config))
                    .toList();

            long startMs = System.currentTimeMillis();
            List<OcrExtractionResult> batch = retryPolicy.execute(
                    "extractBatch:" + slice.size(),
                    () -> timeout.submitWithTimeout(
                            "ocr-batch-" + provider.id() + "-" + offset,
                            () -> provider.extractBatch(requests),
                            properties.effectiveTimeoutMs(provider.id()) * slice.size()));

            if (batch.size() != requests.size()) {
                // The SPI requires positional alignment. A provider that breaks it would silently
                // attach OCR text to the WRONG document — for legal records, that is the worst
                // possible failure, and it must be loud.
                throw new OcrProviderException(provider.id(),
                        "Provider returned " + batch.size() + " results for " + requests.size()
                        + " requests — batch results must align positionally with requests",
                        false, null);
            }

            recordSuccess(provider, System.currentTimeMillis() - startMs);
            batch.forEach(r -> results.add(toPortResult(r)));
        }

        return results;
    }

    private OcrExtractionRequest toRequest(String sourceObjectKey, OcrConfig config) {
        return new OcrExtractionRequest(
                sourceObjectKey,
                config.language(),
                config.enableHandwriting(),
                config.enableTables(),
                config.minConfidenceThreshold(),
                batchSizer.pageBatchHint());
    }

    /** Straight mapping — the runtime does not reinterpret what the engine reported. */
    private OcrResult toPortResult(OcrExtractionResult result) {
        return new OcrResult(
                result.ocrObjectKey(),
                result.pageCount(),
                result.extractedTextLength(),
                result.confidenceAvg(),
                result.warnings(),
                result.durationMs());
    }

    private void recordSuccess(OcrProvider provider, long durationMs) {
        metrics.recordCounter("notarist.ocr.success", "provider", provider.id());
        degradation.markRuntime(RuntimeDegradationManager.AiRuntime.OCR, false, null);
    }

    /**
     * Asking an engine for something it cannot do is not an error — but it must not pass silently
     * either, or a switch to a provider without handwriting support quietly drops the handwritten
     * annotations on an akta and nobody notices until it matters.
     */
    private void warnOnUnsupportedOptions(OcrProvider provider, OcrConfig config) {
        OcrCapabilities capabilities = provider.capabilities();

        if (config.enableHandwriting() && !capabilities.supportsHandwriting()) {
            log.warn("OCR provider '{}' does not support handwriting, but handwriting was requested. "
                    + "Handwritten content will be missed.", provider.id());
        }
        if (config.enableTables() && !capabilities.supportsTables()) {
            log.warn("OCR provider '{}' does not support table extraction, but tables were requested. "
                    + "Table structure will be flattened.", provider.id());
        }
        if (capabilities.rejectsLanguage(config.language())) {
            log.warn("OCR provider '{}' does not advertise language '{}' (supported: {}). "
                    + "Accuracy will be degraded.",
                    provider.id(), config.language(), capabilities.supportedLanguages());
        }
    }
}
