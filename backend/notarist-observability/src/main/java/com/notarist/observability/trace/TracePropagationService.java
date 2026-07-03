package com.notarist.observability.trace;

import com.notarist.observability.log.StructuredLogContract;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Propagates trace and correlation context across module boundaries via SLF4J MDC.
 *
 * TraceContext is the canonical carrier — built from an incoming HTTP request or
 * generated fresh for internally-initiated operations (ingestion jobs, batch tasks).
 *
 * Usage pattern:
 *   try (var ctx = traceService.bind(traceContext)) {
 *       // all log statements inside inherit MDC fields
 *   }
 *
 * Thread safety: MDC is ThreadLocal; cross-thread propagation requires explicit
 * snapshot + restore (see propagateToThread).
 */
@Component
public class TracePropagationService {

    public record TraceContext(
            String correlationId,
            String traceId,
            String tenantId,
            String userId,
            String module,
            String operation,
            String degradationMode
    ) {
        public static TraceContext of(String correlationId, String traceId,
                                      String tenantId, String userId,
                                      String module, String operation) {
            return new TraceContext(correlationId, traceId, tenantId, userId, module, operation, "FULL");
        }

        public static TraceContext generate(String tenantId, String userId,
                                             String module, String operation) {
            return new TraceContext(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    tenantId, userId, module, operation, "FULL");
        }

        public TraceContext withDegradationMode(String mode) {
            return new TraceContext(correlationId, traceId, tenantId, userId, module, operation, mode);
        }

        public TraceContext withOperation(String op) {
            return new TraceContext(correlationId, traceId, tenantId, userId, module, op, degradationMode);
        }
    }

    public final class BoundContext implements AutoCloseable {
        private final Map<String, String> previous;

        BoundContext(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            MDC.clear();
            if (!previous.isEmpty()) MDC.setContextMap(previous);
        }
    }

    public BoundContext bind(TraceContext ctx) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (previous == null) previous = Map.of();

        MDC.put(StructuredLogContract.KEY_CORRELATION_ID,   ctx.correlationId());
        MDC.put(StructuredLogContract.KEY_TRACE_ID,         ctx.traceId());
        MDC.put(StructuredLogContract.KEY_TENANT_ID,        ctx.tenantId() != null ? ctx.tenantId() : "none");
        MDC.put(StructuredLogContract.KEY_USER_ID,          ctx.userId()   != null ? ctx.userId()   : "none");
        MDC.put(StructuredLogContract.KEY_MODULE,           ctx.module());
        MDC.put(StructuredLogContract.KEY_OPERATION,        ctx.operation());
        MDC.put(StructuredLogContract.KEY_DEGRADATION_MODE, ctx.degradationMode() != null ? ctx.degradationMode() : "FULL");

        return new BoundContext(previous);
    }

    /**
     * Executes a supplier within the given trace context, restoring MDC afterward.
     */
    public <T> T withContext(TraceContext ctx, Supplier<T> action) {
        try (BoundContext ignored = bind(ctx)) {
            return action.get();
        }
    }

    public void runWithContext(TraceContext ctx, Runnable action) {
        try (BoundContext ignored = bind(ctx)) {
            action.run();
        }
    }

    /**
     * Captures current MDC as an immutable snapshot for cross-thread propagation.
     */
    public Map<String, String> captureSnapshot() {
        Map<String, String> map = MDC.getCopyOfContextMap();
        return map != null ? Map.copyOf(map) : Map.of();
    }

    /**
     * Restores a captured MDC snapshot onto the current thread (e.g., in a worker thread).
     */
    public AutoCloseable restoreSnapshot(Map<String, String> snapshot) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.setContextMap(snapshot);
        return () -> {
            MDC.clear();
            if (previous != null && !previous.isEmpty()) MDC.setContextMap(previous);
        };
    }

    public String currentCorrelationId() {
        String v = MDC.get(StructuredLogContract.KEY_CORRELATION_ID);
        return v != null ? v : "none";
    }

    public String currentTraceId() {
        String v = MDC.get(StructuredLogContract.KEY_TRACE_ID);
        return v != null ? v : "none";
    }
}
