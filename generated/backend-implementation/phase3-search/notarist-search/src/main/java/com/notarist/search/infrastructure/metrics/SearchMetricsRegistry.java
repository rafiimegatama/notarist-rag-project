package com.notarist.search.infrastructure.metrics;

import com.notarist.search.domain.model.GroundingScore;
import com.notarist.search.domain.model.SearchIntent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SearchMetricsRegistry {

    private static final String PREFIX = "notarist.search";

    private final MeterRegistry meterRegistry;
    private final Counter queriesStarted;
    private final Counter queriesCompleted;
    private final Counter queriesFailed;
    private final Timer queryTimer;

    public SearchMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.queriesStarted = Counter.builder(PREFIX + ".queries.started")
                .description("Total search queries started")
                .register(meterRegistry);

        this.queriesCompleted = Counter.builder(PREFIX + ".queries.completed")
                .description("Total search queries completed successfully")
                .register(meterRegistry);

        this.queriesFailed = Counter.builder(PREFIX + ".queries.failed")
                .description("Total search queries that failed with an exception")
                .register(meterRegistry);

        this.queryTimer = Timer.builder(PREFIX + ".query.duration")
                .description("End-to-end search query processing time in ms")
                .register(meterRegistry);
    }

    public void recordQueryStarted(SearchIntent intent) {
        queriesStarted.increment();
        Counter.builder(PREFIX + ".queries.started.by_intent")
                .description("Queries started, broken down by classified intent")
                .tag("intent", intent != null ? intent.name() : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordQueryCompleted(SearchIntent intent, GroundingScore.Level groundingLevel, long processingMs) {
        queriesCompleted.increment();
        queryTimer.record(processingMs, TimeUnit.MILLISECONDS);
        Counter.builder(PREFIX + ".queries.completed.by_grounding")
                .description("Completed queries broken down by grounding level and intent")
                .tag("grounding", groundingLevel != null ? groundingLevel.name() : "UNKNOWN")
                .tag("intent", intent != null ? intent.name() : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordQueryFailed() {
        queriesFailed.increment();
    }
}
