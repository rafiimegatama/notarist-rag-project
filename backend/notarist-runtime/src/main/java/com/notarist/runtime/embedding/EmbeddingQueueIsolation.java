package com.notarist.runtime.embedding;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Dedicated thread pool for embedding generation.
 *
 * Embedding is network-bound (HTTP to bge-m3 service) with medium latency.
 * Larger pool than OCR because embedding batches are parallelizable.
 *
 * Pool sizing:
 *   corePoolSize = 4   — four concurrent embedding batches
 *   maxPoolSize  = 8   — burst capacity
 *   queueCapacity = 50 — larger queue for ingestion batch processing
 */
@Component
public class EmbeddingQueueIsolation {

    private final ThreadPoolExecutor executor;

    public EmbeddingQueueIsolation(MeterRegistry meterRegistry) {
        this.executor = new ThreadPoolExecutor(
                4, 8,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                r -> { Thread t = new Thread(r, "embedding-runtime"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());

        Gauge.builder("notarist.runtime.embedding.queue.size", executor, e -> (double) e.getQueue().size())
                .description("Embedding queue depth")
                .register(meterRegistry);
        Gauge.builder("notarist.runtime.embedding.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active embedding worker threads")
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
        return executor.getQueue().size() >= 40;  // 80% of max queue
    }
}
