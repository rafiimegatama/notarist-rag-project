package com.notarist.audit.api.response;

import java.util.Map;
import java.util.UUID;

public record AuditEntryResponse(
    UUID auditId,
    String correlationId,
    String eventType,
    String eventCategory,
    String subjectType,
    String subjectId,
    UUID actorUserId,
    String actorRole,
    UUID tenantId,
    String action,
    String outcome,
    Map<String, Object> detailJson,
    String ipAddress,
    String createdAt
) {}
