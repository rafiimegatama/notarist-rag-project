package com.notarist.observability.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralised operational metrics registry for ingestion pipeline and AI runtime.
 *
 * Metrics exposed (all prefixed notarist.ops.*):
 *   ingestion.lag          — time between document upload and ingest-complete (ms, Gauge)
 *   ocr.throughput         — pages processed per second (Gauge)
 *   embedding.lag          — time from chunk-ready to vector-indexed (ms, Timer)
 *   vector.indexing.lag    — time from embedding-done to Qdrant-indexed (ms, Timer)
 *   reranker.latency       — cross-encoder call duration (ms, Timer)
 *   inference.latency      — Ollama generation latency (ms, Timer)
 *   timeout.rate           — rolling timeout count (Counter)
 *   cancellation.rate      — rolling cancellation count (Counter)
 *   degraded.mode.count    — times degradation level changed (Counter per level)
 *   queue.depth.*          — current queue depth per pool (Gauge, supplied externally)
 */
@Component
public class OperationalMetricsRegistry {

    private final MeterRegistry meterRegistry;

    private final AtomicLong ingestionLagMs    = new AtomicLong(0L);
    private final AtomicLong ocrPagesPerSecond = new AtomicLong(0L);

    private final Timer  embeddingLagTimer;
    private final Timer  vectorIndexingLagTimer;
    private final Timer  rerankerLatencyTimer;
    private final Timer  inferenceLatencyTimer;

    private final Counter timeoutCounter;
    private final Counter cancellationCounter;

    public OperationalMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("notarist.ops.ingestion.lag", ingestionLagMs, AtomicLong::get)
                .description("Time from document upload to ingest complete (ms)")
                .register(meterRegistry);

        Gauge.builder("notarist.ops.ocr.throughput", ocrPagesPerSecond, AtomicLong::get)
                .description("OCR pages processed per second")
                .register(meterRegistry);

        this.embeddingLagTimer = Timer.builder("notarist.ops.embedding.lag")
                .description("Time from chunk-ready to vector embedding complete")
                .register(meterRegistry);

        this.vectorIndexingLagTimer = Timer.builder("notarist.ops.vector.indexing.lag")
                .description("Time from embedding done to Qdrant indexed")
                .register(meterRegistry);

        this.rerankerLatencyTimer = Timer.builder("notarist.ops.reranker.latency")
                .description("Cross-encoder reranker call duration")
                .register(meterRegistry);

        this.inferenceLatencyTimer = Timer.builder("notarist.ops.inference.latency")
                .description("Ollama LLM generation latency")
                .register(meterRegistry);

        this.timeoutCounter = Counter.builder("notarist.ops.timeout.total")
                .description("Total number of timeout events across all integrations")
                .register(meterRegistry);

        this.cancellationCounter = Counter.builder("notarist.ops.cancellation.total")
                .description("Total number of on-demand cancellations")
                .register(meterRegistry);
    }

    public void updateIngestionLag(long lagMs)        { ingestionLagMs.set(lagMs); }
    public void updateOcrThroughput(long pagesPerSec) { ocrPagesPerSecond.set(pagesPerSec); }

    public void recordEmbeddingLag(long ms)      { embeddingLagTimer.record(ms, TimeUnit.MILLISECONDS); }
    public void recordVectorIndexingLag(long ms) { vectorIndexingLagTimer.record(ms, TimeUnit.MILLISECONDS); }
    public void recordRerankerLatency(long ms)   { rerankerLatencyTimer.record(ms, TimeUnit.MILLISECONDS); }
    public void recordInferenceLatency(long ms)  { inferenceLatencyTimer.record(ms, TimeUnit.MILLISECONDS); }

    public void incrementTimeout()       { timeoutCounter.increment(); }
    public void incrementCancellation()  { cancellationCounter.increment(); }

    public void recordDegradationTransition(String fromLevel, String toLevel) {
        Counter.builder("notarist.ops.degradation.transition")
                .tag("from", fromLevel)
                .tag("to",   toLevel)
                .description("Degradation level transition count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Registers an external queue-depth supplier as a Gauge.
     * Call once per queue at startup from the isolation component.
     */
    public void registerQueueDepthGauge(String queueName, java.util.function.Supplier<Integer> depthSupplier) {
        Gauge.builder("notarist.ops.queue.depth", depthSupplier, Supplier::get)
                .tag("queue", queueName)
                .description("Current depth of the named processing queue")
                .register(meterRegistry);
    }
}
