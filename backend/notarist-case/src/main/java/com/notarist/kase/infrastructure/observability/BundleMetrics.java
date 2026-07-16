package com.notarist.kase.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Micrometer instrumentation for the Bundle bounded context. */
@Component
public class BundleMetrics {

    private final MeterRegistry registry;
    private final Counter bundlesCreated;
    private final Timer openBundleTimer;
    private final Timer changeStatusTimer;

    public BundleMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.bundlesCreated = Counter.builder("notarist_bundle_created_total")
                .description("Bundles opened").register(registry);
        this.openBundleTimer = Timer.builder("notarist_bundle_open_seconds")
                .description("Latency of opening a bundle").register(registry);
        this.changeStatusTimer = Timer.builder("notarist_bundle_status_change_seconds")
                .description("Latency of a bundle status transition").register(registry);
    }

    public void recordBundleCreated(String bundleType) {
        bundlesCreated.increment();
        registry.counter("notarist_bundle_created_by_type_total", "type", bundleType).increment();
    }

    public void recordTransition(String fromStatus, String toStatus) {
        registry.counter("notarist_bundle_transition_total", "from", fromStatus, "to", toStatus).increment();
    }

    public Timer openBundleTimer() { return openBundleTimer; }

    public Timer changeStatusTimer() { return changeStatusTimer; }
}
