package com.notarist.runtime.metrics;

import com.notarist.runtime.model.ModelProvider;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralised Micrometer metrics for all AI runtime adapters.
 *
 * Per-model metrics:
 *   - inference latency (Timer)  — records actual call duration
 *   - timeout count (Counter)    — incremented on TimeoutException
 *   - model load time (Timer)    — time to load/warm-up model
 *   - tokens/sec (Gauge)         — updated by OllamaRuntimeAdapter on each response
 *   - OOM count (Counter)        — incremented on OutOfMemoryError from Ollama
 */
@Component
public class RuntimeMetricsRegistry {

    private final MeterRegistry meterRegistry;

    private final Map<ModelProvider, Timer>   latencyTimers  = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, Counter> timeoutCounters = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, Timer>   loadTimers     = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, Counter> oomCounters    = new EnumMap<>(ModelProvider.class);

    private final ConcurrentHashMap<ModelProvider, AtomicLong> tokenRates = new ConcurrentHashMap<>();

    public RuntimeMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        for (ModelProvider provider : ModelProvider.values()) {
            String tag = provider.name().toLowerCase();

            latencyTimers.put(provider,
                    Timer.builder("notarist.runtime.inference.latency")
                            .tag("provider", tag)
                            .description("AI inference call latency")
                            .register(meterRegistry));

            timeoutCounters.put(provider,
                    Counter.builder("notarist.runtime.inference.timeout")
                            .tag("provider", tag)
                            .description("Inference timeout count")
                            .register(meterRegistry));

            loadTimers.put(provider,
                    Timer.builder("notarist.runtime.model.load.time")
                            .tag("provider", tag)
                            .description("Time to load / warm-up model")
                            .register(meterRegistry));

            oomCounters.put(provider,
                    Counter.builder("notarist.runtime.inference.oom")
                            .tag("provider", tag)
                            .description("Out-of-memory errors from runtime")
                            .register(meterRegistry));

            AtomicLong rate = new AtomicLong(0L);
            tokenRates.put(provider, rate);
            Gauge.builder("notarist.runtime.inference.tokens_per_sec",
                            rate, AtomicLong::get)
                    .tag("provider", tag)
                    .description("Last observed token generation rate (tokens/sec)")
                    .register(meterRegistry);
        }
    }

    public void recordInferenceLatency(ModelProvider provider, long durationMs) {
        Timer timer = latencyTimers.get(provider);
        if (timer != null) timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTimeout(ModelProvider provider) {
        Counter counter = timeoutCounters.get(provider);
        if (counter != null) counter.increment();
    }

    public void recordModelLoadTime(ModelProvider provider, long durationMs) {
        Timer timer = loadTimers.get(provider);
        if (timer != null) timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordOom(ModelProvider provider) {
        Counter counter = oomCounters.get(provider);
        if (counter != null) counter.increment();
    }

    public void updateTokenRate(ModelProvider provider, long tokensPerSecond) {
        AtomicLong rate = tokenRates.get(provider);
        if (rate != null) rate.set(tokensPerSecond);
    }

    public void recordCounter(String metricName, String tagKey, String tagValue) {
        Counter.builder(metricName)
                .tag(tagKey, tagValue)
                .register(meterRegistry)
                .increment();
    }
}
