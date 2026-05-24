package com.notarist.observability.log;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines the mandatory structured log schema for all notarist modules.
 *
 * Every log entry MUST include the mandatory fields below.
 * Optional fields are added via withField() before emit().
 *
 * MDC keys are constants — cross-module consistency depends on using these constants,
 * not string literals, in call sites.
 *
 * Forbidden fields (enforced by LogSanitizer at emit time):
 *   raw_text, ocr_dump, jwt_token, password, secret, pii_*
 */
public final class StructuredLogContract {

    // Mandatory MDC keys — all modules must populate these via TracePropagationService
    public static final String KEY_CORRELATION_ID   = "correlation_id";
    public static final String KEY_TRACE_ID         = "trace_id";
    public static final String KEY_TENANT_ID        = "tenant_id";
    public static final String KEY_USER_ID          = "user_id";
    public static final String KEY_MODULE           = "module";
    public static final String KEY_OPERATION        = "operation";
    public static final String KEY_LATENCY_MS       = "latency_ms";
    public static final String KEY_RESULT_STATUS    = "result_status";
    public static final String KEY_DEGRADATION_MODE = "degradation_mode";

    // Optional keys
    public static final String KEY_DOCUMENT_ID      = "document_id";
    public static final String KEY_CHUNK_COUNT      = "chunk_count";
    public static final String KEY_PROMPT_VERSION   = "prompt_version";
    public static final String KEY_RETRIEVAL_SNAP   = "retrieval_snapshot_id";
    public static final String KEY_CONFIDENCE       = "confidence";
    public static final String KEY_QUEUE_DEPTH      = "queue_depth";
    public static final String KEY_CIRCUIT_STATE    = "circuit_state";
    public static final String KEY_ERROR_TYPE       = "error_type";

    public enum ResultStatus { SUCCESS, FAILURE, DEGRADED, TIMEOUT, CANCELLED, REJECTED }

    private StructuredLogContract() {}

    /**
     * Fluent builder for a single structured log event.
     * Reads mandatory fields from MDC automatically; override as needed.
     */
    public static final class Entry {

        private final Map<String, Object> fields = new LinkedHashMap<>();
        private final Logger              logger;
        private String                    message;

        private Entry(Logger logger) {
            this.logger = logger;
            fields.put("timestamp",        Instant.now().toString());
            fields.put(KEY_CORRELATION_ID, mdc(KEY_CORRELATION_ID));
            fields.put(KEY_TRACE_ID,       mdc(KEY_TRACE_ID));
            fields.put(KEY_TENANT_ID,      mdc(KEY_TENANT_ID));
            fields.put(KEY_USER_ID,        mdc(KEY_USER_ID));
            fields.put(KEY_MODULE,         mdc(KEY_MODULE));
            fields.put(KEY_OPERATION,      mdc(KEY_OPERATION));
            fields.put(KEY_DEGRADATION_MODE, mdc(KEY_DEGRADATION_MODE));
        }

        public static Entry of(Logger logger) { return new Entry(logger); }

        public Entry message(String msg)             { this.message = msg; return this; }
        public Entry operation(String op)            { fields.put(KEY_OPERATION, op);       return this; }
        public Entry module(String mod)              { fields.put(KEY_MODULE, mod);         return this; }
        public Entry latencyMs(long ms)              { fields.put(KEY_LATENCY_MS, ms);      return this; }
        public Entry status(ResultStatus rs)         { fields.put(KEY_RESULT_STATUS, rs.name()); return this; }
        public Entry degradationMode(String mode)    { fields.put(KEY_DEGRADATION_MODE, mode); return this; }
        public Entry correlationId(String id)        { fields.put(KEY_CORRELATION_ID, id);  return this; }
        public Entry tenantId(String id)             { fields.put(KEY_TENANT_ID, id);       return this; }
        public Entry withField(String key, Object v) { fields.put(key, v);                  return this; }

        public void info()  { logger.info(toKvLine()); }
        public void warn()  { logger.warn(toKvLine()); }
        public void error() { logger.error(toKvLine()); }
        public void debug() { logger.debug(toKvLine()); }

        private String toKvLine() {
            StringBuilder sb = new StringBuilder();
            if (message != null) sb.append(message).append(' ');
            fields.forEach((k, v) -> {
                if (v != null) sb.append(k).append('=').append(sanitizeValue(v.toString())).append(' ');
            });
            return sb.toString().stripTrailing();
        }

        private static String sanitizeValue(String v) {
            if (v.contains(" ")) return "\"" + v.replace("\"", "'") + "\"";
            return v;
        }

        private static String mdc(String key) {
            String val = MDC.get(key);
            return val != null ? val : "none";
        }
    }
}
