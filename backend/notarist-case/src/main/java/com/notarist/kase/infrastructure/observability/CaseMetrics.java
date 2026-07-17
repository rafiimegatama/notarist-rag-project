package com.notarist.kase.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Micrometer instrumentation for the Case module. Counts case creations and status transitions
 * (tagged by from/to state and transition kind, so "how often are drafts rolled back?" is a
 * dashboard query), and times the two write use cases. Exposed on the actuator Prometheus endpoint
 * that the web module already publishes — no new scrape target.
 */
@Component
public class CaseMetrics {

    private final MeterRegistry registry;
    private final Counter casesCreated;
    private final Timer openCaseTimer;
    private final Timer changeStatusTimer;

    public CaseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.casesCreated = Counter.builder("notarist_case_created_total")
                .description("Cases opened")
                .register(registry);
        this.openCaseTimer = Timer.builder("notarist_case_open_seconds")
                .description("Latency of opening a case")
                .register(registry);
        this.changeStatusTimer = Timer.builder("notarist_case_status_change_seconds")
                .description("Latency of a case status transition")
                .register(registry);
    }

    public void recordCaseCreated(String caseType) {
        casesCreated.increment();
        registry.counter("notarist_case_created_by_type_total", "type", caseType).increment();
    }

    public void recordTransition(String fromState, String toState, String kind) {
        registry.counter("notarist_case_transition_total",
                "from", fromState, "to", toState, "kind", kind).increment();
    }

    public Timer openCaseTimer() { return openCaseTimer; }

    public Timer changeStatusTimer() { return changeStatusTimer; }
}
