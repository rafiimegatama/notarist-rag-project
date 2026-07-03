package com.notarist.core.api.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Cross-module audit event — published via Spring ApplicationEventPublisher.
 * Lives in core so all modules can emit without depending on notarist-audit.
 */
public record AuditEventPayload(
        String eventType,
        String subjectType,
        String subjectId,
        UUID actorUserId,
        String actorRole,
        UUID tenantId,
        String action,
        String outcome,
        String ipAddress,
        String correlationId,
        Map<String, Object> detail
) {
    public AuditEventPayload {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType required");
        if (outcome == null || outcome.isBlank()) throw new IllegalArgumentException("outcome required");
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }
}
