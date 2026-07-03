package com.notarist.runtime.ollama;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Dedicated thread pool for LLM inference (Ollama).
 *
 * LLM inference is single-threaded on CPU (one model loads at a time).
 * Pool size of 1 enforces sequential inference to prevent OOM and model contention.
 * Queue of 5: beyond this, backpressure prevents assistant from accepting more requests.
 *
 * On GPU with sufficient VRAM, increase corePoolSize to 2-4.
 * See GpuAwarenessConfig for dynamic sizing based on detected capability.
 */
@Component
public class InferenceQueueIsolation {

    private final ThreadPoolExecutor executor;

    public InferenceQueueIsolation(MeterRegistry meterRegistry) {
        this.executor = new ThreadPoolExecutor(
                1, 1,   // single-threaded for CPU; override for GPU in GpuAwarenessConfig
                0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5),
                r -> { Thread t = new Thread(r, "llm-inference"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());    // reject when full — caller handles

        Gauge.builder("notarist.runtime.inference.queue.size", executor, e -> (double) e.getQueue().size())
                .description("LLM inference queue depth")
                .register(meterRegistry);
        Gauge.builder("notarist.runtime.inference.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active LLM inference threads")
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
        return executor.getQueue().size() >= 4;   // 80% — reject new requests early
    }
}
