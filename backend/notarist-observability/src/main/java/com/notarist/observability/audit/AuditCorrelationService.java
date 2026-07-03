package com.notarist.observability.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Links all trace identifiers for a single AI interaction into a queryable record.
 *
 * Every AI assistant request generates 5 traces that must remain correlated:
 *   userTrace        — who made the request (userId, tenantId, sessionId, correlationId)
 *   retrievalTrace   — what was retrieved (snapshotId, fusionStrategy, chunkIds)
 *   promptTrace      — what prompt was built (promptVersion, contextTokens, truncated)
 *   responseTrace    — what was generated (traceId, processingMs, confidence, streaming)
 *   citationTrace    — which sources were cited (documentIds, citationCount, sourceKeys)
 *
 * The AuditCorrelationRecord is the durable audit unit — persisted to structured log.
 * In-memory map is bounded to MAX_ACTIVE_CORRELATIONS to prevent unbounded growth.
 */
@Component
public class AuditCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(AuditCorrelationService.class);
    private static final int    MAX_ACTIVE_CORRELATIONS = 500;

    public record UserTrace(
            String userId,
            String tenantId,
            String sessionId,
            String correlationId,
            Instant requestTime
    ) {}

    public record RetrievalTrace(
            String   retrievalSnapshotId,
            String   fusionStrategy,
            int      candidateCount,
            int      rerankedCount,
            List<String> topChunkIds
    ) {}

    public record PromptTrace(
            String  promptVersion,
            int     contextTokens,
            boolean wasTruncated,
            int     droppedChunkCount
    ) {}

    public record ResponseTrace(
            String  traceId,
            long    processingMs,
            String  confidence,
            boolean wasStreamed,
            boolean wasShortCircuited
    ) {}

    public record CitationTrace(
            List<String> documentIds,
            List<String> sourceObjectKeys,
            int          citationCount
    ) {}

    public record AuditCorrelationRecord(
            String        correlationId,
            UserTrace     userTrace,
            RetrievalTrace retrievalTrace,
            PromptTrace   promptTrace,
            ResponseTrace responseTrace,
            CitationTrace citationTrace,
            Instant       completedAt
    ) {
        public boolean isComplete() {
            return userTrace != null && retrievalTrace != null
                    && promptTrace != null && responseTrace != null && citationTrace != null;
        }
    }

    private final ConcurrentHashMap<String, Builder> inProgress =
            new ConcurrentHashMap<>();

    public static final class Builder {
        String correlationId;
        UserTrace     userTrace;
        RetrievalTrace retrievalTrace;
        PromptTrace   promptTrace;
        ResponseTrace responseTrace;
        CitationTrace citationTrace;

        public Builder correlationId(String id) { this.correlationId = id; return this; }
        public Builder user(UserTrace t)          { this.userTrace = t;         return this; }
        public Builder retrieval(RetrievalTrace t){ this.retrievalTrace = t;    return this; }
        public Builder prompt(PromptTrace t)      { this.promptTrace = t;       return this; }
        public Builder response(ResponseTrace t)  { this.responseTrace = t;     return this; }
        public Builder citations(CitationTrace t) { this.citationTrace = t;     return this; }

        public AuditCorrelationRecord build() {
            return new AuditCorrelationRecord(correlationId, userTrace, retrievalTrace,
                    promptTrace, responseTrace, citationTrace, Instant.now());
        }
    }

    public void begin(String correlationId, UserTrace userTrace) {
        if (inProgress.size() >= MAX_ACTIVE_CORRELATIONS) {
            log.warn("AuditCorrelationService: max active correlations reached — evicting oldest");
            evictOldest();
        }
        Builder builder = new Builder();
        builder.correlationId(correlationId).user(userTrace);
        inProgress.put(correlationId, builder);
    }

    public void attachRetrieval(String correlationId, RetrievalTrace trace) {
        withBuilder(correlationId, b -> b.retrieval(trace));
    }

    public void attachPrompt(String correlationId, PromptTrace trace) {
        withBuilder(correlationId, b -> b.prompt(trace));
    }

    public void attachResponse(String correlationId, ResponseTrace trace) {
        withBuilder(correlationId, b -> b.response(trace));
    }

    public void attachCitations(String correlationId, CitationTrace trace) {
        withBuilder(correlationId, b -> b.citations(trace));
    }

    /**
     * Completes the correlation record and emits a structured audit log entry.
     * Removes the in-progress entry.
     */
    public Optional<AuditCorrelationRecord> complete(String correlationId) {
        Builder builder = inProgress.remove(correlationId);
        if (builder == null) {
            log.warn("AuditCorrelationService: no in-progress correlation for id={}", correlationId);
            return Optional.empty();
        }
        AuditCorrelationRecord record = builder.build();
        emitAuditLog(record);
        return Optional.of(record);
    }

    private void withBuilder(String correlationId, java.util.function.Consumer<Builder> action) {
        Builder builder = inProgress.get(correlationId);
        if (builder != null) action.accept(builder);
        else log.debug("AuditCorrelationService: no builder for correlationId={}", correlationId);
    }

    private void emitAuditLog(AuditCorrelationRecord r) {
        log.info("audit=AI_INTERACTION correlationId={} userId={} tenantId={} sessionId={} "
                        + "snapshotId={} promptVersion={} traceId={} processingMs={} "
                        + "confidence={} citationCount={} isComplete={}",
                r.correlationId(),
                r.userTrace()     != null ? r.userTrace().userId()                  : "none",
                r.userTrace()     != null ? r.userTrace().tenantId()                : "none",
                r.userTrace()     != null ? r.userTrace().sessionId()               : "none",
                r.retrievalTrace()!= null ? r.retrievalTrace().retrievalSnapshotId(): "none",
                r.promptTrace()   != null ? r.promptTrace().promptVersion()         : "none",
                r.responseTrace() != null ? r.responseTrace().traceId()             : "none",
                r.responseTrace() != null ? r.responseTrace().processingMs()        : 0,
                r.responseTrace() != null ? r.responseTrace().confidence()          : "none",
                r.citationTrace() != null ? r.citationTrace().citationCount()       : 0,
                r.isComplete());
    }

    private void evictOldest() {
        inProgress.keySet().stream().findFirst().ifPresent(inProgress::remove);
    }
}
