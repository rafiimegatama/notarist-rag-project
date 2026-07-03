package com.notarist.assistant.application.port.out;

import com.notarist.assistant.domain.model.AnswerConfidence;
import com.notarist.assistant.domain.model.AssistantSafetyMode;

import java.util.UUID;

/**
 * Output port for AI interaction audit events.
 * Implemented by AssistantAuditPublisher (SLF4J logging stub in Phase 4).
 */
public interface AssistantAuditPort {

    void publishInteraction(AuditEvent event);

    record AuditEvent(
            UUID traceId,
            UUID sessionId,
            UUID tenantId,
            UUID userId,
            String userQuery,
            String promptVersion,
            UUID retrievalSnapshotId,
            AnswerConfidence confidence,
            AssistantSafetyMode safetyMode,
            boolean hallucinationWarning,
            boolean downgraded,
            long processingMs
    ) {}
}
