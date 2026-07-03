package com.notarist.assistant.infrastructure.metrics;

import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AssistantMetricsRegistry {

    private static final String PREFIX = "notarist.assistant";

    private final MeterRegistry meterRegistry;
    private final Counter interactionsStarted;
    private final Counter interactionsCompleted;
    private final Counter interactionsFailed;
    private final Counter hallucinationWarnings;
    private final Counter downgrades;
    private final Timer   interactionTimer;

    public AssistantMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.interactionsStarted = Counter.builder(PREFIX + ".interactions.started")
                .description("Total assistant interactions started")
                .register(meterRegistry);

        this.interactionsCompleted = Counter.builder(PREFIX + ".interactions.completed")
                .description("Total assistant interactions completed successfully")
                .register(meterRegistry);

        this.interactionsFailed = Counter.builder(PREFIX + ".interactions.failed")
                .description("Total assistant interactions that threw an exception")
                .register(meterRegistry);

        this.hallucinationWarnings = Counter.builder(PREFIX + ".hallucination.warnings")
                .description("Total responses with at least one hallucination/unsupported-claim warning")
                .register(meterRegistry);

        this.downgrades = Counter.builder(PREFIX + ".responses.downgraded")
                .description("Total responses downgraded to fallback message by HallucinationGuard")
                .register(meterRegistry);

        this.interactionTimer = Timer.builder(PREFIX + ".interaction.duration")
                .description("End-to-end assistant interaction processing time")
                .register(meterRegistry);
    }

    public void recordInteractionStarted(AssistantSafetyMode safetyMode) {
        interactionsStarted.increment();
        Counter.builder(PREFIX + ".interactions.started.by_mode")
                .description("Interactions started broken down by safety mode")
                .tag("safety_mode", safetyMode != null ? safetyMode.name() : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordInteractionCompleted(AnswerConfidence confidence, long processingMs) {
        interactionsCompleted.increment();
        interactionTimer.record(processingMs, TimeUnit.MILLISECONDS);
        Counter.builder(PREFIX + ".interactions.completed.by_confidence")
                .description("Completed interactions broken down by answer confidence")
                .tag("confidence", confidence != null ? confidence.name() : "UNKNOWN")
                .register(meterRegistry)
                .increment();
    }

    public void recordInteractionFailed() {
        interactionsFailed.increment();
    }

    public void recordHallucinationWarning() {
        hallucinationWarnings.increment();
    }

    public void recordDowngrade() {
        downgrades.increment();
    }
}
