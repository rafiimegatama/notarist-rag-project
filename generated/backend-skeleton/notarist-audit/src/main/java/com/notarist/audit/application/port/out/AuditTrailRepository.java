package com.notarist.audit.application.port.out;

import com.notarist.audit.domain.model.AuditEntry;
import com.notarist.audit.domain.model.AuditEventType;
import com.notarist.audit.domain.model.AuditOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit trail persistence — Oracle NOTARIST_SEC.AUDIT_TRAIL. */
public interface AuditTrailRepository {
    void append(AuditEntry entry);
    Optional<AuditEntry> findById(UUID auditId);
    List<AuditEntry> findByFilter(AuditFilter filter, int page, int size);
    long countByFilter(AuditFilter filter);

    record AuditFilter(
        UUID tenantId,
        String eventCategory,
        AuditEventType eventType,
        UUID actorUserId,
        String subjectId,
        AuditOutcome outcome,
        Instant dateFrom,
        Instant dateTo
    ) {}
}
