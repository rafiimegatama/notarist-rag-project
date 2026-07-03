package com.notarist.runtime.reranker;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Dedicated thread pool for cross-encoder reranking.
 *
 * Reranking is network-bound (HTTP to reranker service).
 * Isolated from LLM inference to prevent rerank calls from blocking ongoing inference.
 *
 * Pool sizing:
 *   corePoolSize = 2   — two concurrent rerank requests
 *   maxPoolSize  = 4
 *   queueCapacity = 10
 */
@Component
public class RerankerQueueIsolation {

    private final ThreadPoolExecutor executor;

    public RerankerQueueIsolation(MeterRegistry meterRegistry) {
        this.executor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                r -> { Thread t = new Thread(r, "reranker-runtime"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());

        Gauge.builder("notarist.runtime.reranker.queue.size", executor, e -> (double) e.getQueue().size())
                .description("Reranker queue depth")
                .register(meterRegistry);
        Gauge.builder("notarist.runtime.reranker.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active reranker threads")
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
}
