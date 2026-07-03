package com.notarist.assistant.infrastructure.publisher;

import com.notarist.assistant.application.port.out.AssistantAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Audit publisher stub — Phase 4.
 *
 * Logs AI interaction events via SLF4J (structured as key=value pairs for log aggregation).
 * Persistent audit trail (Oracle audit table or event bus) deferred to Phase 5.
 *
 * Log format is designed for easy Kibana / Graylog query:
 *   audit=AI_INTERACTION traceId=X sessionId=Y tenantId=Z ...
 */
@Component
public class AssistantAuditPublisher implements AssistantAuditPort {

    private static final Logger auditLog = LoggerFactory.getLogger("com.notarist.audit.assistant");

    @Override
    public void publishInteraction(AuditEvent event) {
        auditLog.info(
                "audit=AI_INTERACTION traceId={} sessionId={} tenantId={} userId={} " +
                "promptVersion={} retrievalSnapshotId={} confidence={} safetyMode={} " +
                "hallucinationWarning={} downgraded={} processingMs={}",
                event.traceId(),
                event.sessionId(),
                event.tenantId(),
                event.userId(),
                event.promptVersion(),
                event.retrievalSnapshotId(),
                event.confidence(),
                event.safetyMode(),
                event.hallucinationWarning(),
                event.downgraded(),
                event.processingMs());
    }
}
