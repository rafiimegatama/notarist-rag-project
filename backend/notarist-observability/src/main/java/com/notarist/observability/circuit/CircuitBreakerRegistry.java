package com.notarist.observability.circuit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker per external integration.
 *
 * States: CLOSED (normal) → OPEN (blocking) → HALF_OPEN (probing) → CLOSED (recovered)
 *
 * Thresholds (conservative defaults for legal-domain SLAs):
 *   openThreshold    = 3 consecutive failures → OPEN
 *   halfOpenAfterMs  = 30_000 (30s cooldown before probing)
 *   probeSuccesses   = 2 consecutive successes in HALF_OPEN → CLOSED
 *
 * Integrations tracked: OCR, OLLAMA, QDRANT, POSTGRES, MINIO
 *
 * Callers MUST check isCallAllowed() before making the integration call,
 * then recordSuccess() or recordFailure() based on the result.
 */
@Component
public class CircuitBreakerRegistry {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);

    public enum Integration { OCR, OLLAMA, QDRANT, POSTGRES, MINIO }

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final int  OPEN_THRESHOLD    = 3;
    private static final long HALF_OPEN_AFTER_MS = 30_000L;
    private static final int  PROBE_SUCCESSES   = 2;

    record BreakerState(
            State   state,
            int     consecutiveFailures,
            int     probeSuccesses,
            Instant openedAt
    ) {
        static BreakerState closed()   { return new BreakerState(State.CLOSED, 0, 0, null); }
        static BreakerState open()     { return new BreakerState(State.OPEN, OPEN_THRESHOLD, 0, Instant.now()); }
        static BreakerState halfOpen() { return new BreakerState(State.HALF_OPEN, 0, 0, null); }
    }

    private final ConcurrentHashMap<Integration, BreakerState> states = new ConcurrentHashMap<>();

    public CircuitBreakerRegistry(MeterRegistry meterRegistry) {
        for (Integration integration : Integration.values()) {
            states.put(integration, BreakerState.closed());
            String name = integration.name().toLowerCase();
            Gauge.builder("notarist.circuit." + name + ".state",
                            states, s -> stateValue(s.get(integration).state()))
                    .description("Circuit breaker state for " + name + " (0=CLOSED, 1=HALF_OPEN, 2=OPEN)")
                    .register(meterRegistry);
        }
    }

    public boolean isCallAllowed(Integration integration) {
        BreakerState current = states.get(integration);
        if (current == null) return true;

        return switch (current.state()) {
            case CLOSED   -> true;
            case OPEN     -> maybeTransitionToHalfOpen(integration, current);
            case HALF_OPEN -> true;
        };
    }

    public void recordSuccess(Integration integration) {
        states.compute(integration, (k, current) -> {
            if (current == null) return BreakerState.closed();
            if (current.state() == State.HALF_OPEN) {
                int successes = current.probeSuccesses() + 1;
                if (successes >= PROBE_SUCCESSES) {
                    log.info("CircuitBreaker: {} RECOVERED → CLOSED", integration);
                    return BreakerState.closed();
                }
                return new BreakerState(State.HALF_OPEN, 0, successes, null);
            }
            return BreakerState.closed();
        });
    }

    public void recordFailure(Integration integration) {
        states.compute(integration, (k, current) -> {
            if (current == null) current = BreakerState.closed();
            int failures = current.consecutiveFailures() + 1;
            if (current.state() == State.HALF_OPEN || failures >= OPEN_THRESHOLD) {
                log.warn("CircuitBreaker: {} → OPEN (failures={})", integration, failures);
                return BreakerState.open();
            }
            return new BreakerState(State.CLOSED, failures, 0, null);
        });
    }

    public State getState(Integration integration) {
        BreakerState s = states.get(integration);
        return s != null ? s.state() : State.CLOSED;
    }

    public Map<Integration, State> snapshotStates() {
        Map<Integration, State> snapshot = new EnumMap<>(Integration.class);
        states.forEach((k, v) -> snapshot.put(k, v.state()));
        return snapshot;
    }

    private boolean maybeTransitionToHalfOpen(Integration integration, BreakerState current) {
        if (current.openedAt() == null) return false;
        long elapsedMs = Instant.now().toEpochMilli() - current.openedAt().toEpochMilli();
        if (elapsedMs >= HALF_OPEN_AFTER_MS) {
            states.put(integration, BreakerState.halfOpen());
            log.info("CircuitBreaker: {} → HALF_OPEN (elapsed={}ms)", integration, elapsedMs);
            return true;
        }
        return false;
    }

    private static double stateValue(State state) {
        return switch (state) {
            case CLOSED   -> 0.0;
            case HALF_OPEN -> 1.0;
            case OPEN     -> 2.0;
        };
    }
}
