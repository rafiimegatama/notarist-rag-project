package com.notarist.runtime.ocr;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Dedicated thread pool for OCR operations.
 *
 * OCR is CPU-bound and latency-insensitive relative to search/inference.
 * Isolating it prevents OCR jobs from blocking the embedding or inference pools.
 *
 * Pool sizing:
 *   corePoolSize = 2   — two concurrent OCR documents max (CPU heavy)
 *   maxPoolSize  = 4   — burst capacity for batch ingestion
 *   queueCapacity = 20 — bounded queue prevents unbounded memory growth
 *
 * Rejection policy: CallerRunsPolicy — backpressure on the ingestion worker thread.
 * This prevents dropping OCR jobs silently while still applying backpressure.
 */
@Component
public class OcrRuntimeIsolation {

    private final ThreadPoolExecutor executor;

    public OcrRuntimeIsolation(MeterRegistry meterRegistry) {
        this.executor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20),
                r -> { Thread t = new Thread(r, "ocr-runtime"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());

        Gauge.builder("notarist.runtime.ocr.queue.size", executor, ThreadPoolExecutor::getQueue)
                .description("OCR queue depth")
                .register(meterRegistry);
        Gauge.builder("notarist.runtime.ocr.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active OCR worker threads")
                .register(meterRegistry);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    public Executor asExecutor() {
        return executor;
    }

    public int queueDepth() {
        return executor.getQueue().size();
    }

    public boolean isSaturated() {
        return executor.getQueue().size() >= 15;  // 75% of max queue
    }
}
