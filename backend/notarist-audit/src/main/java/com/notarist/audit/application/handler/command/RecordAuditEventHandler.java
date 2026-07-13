package com.notarist.audit.application.handler.command;

import com.notarist.audit.application.port.in.RecordAuditEventUseCase;
import com.notarist.audit.application.port.out.AuditTrailRepository;
import com.notarist.audit.domain.model.AuditEntry;
import org.springframework.stereotype.Service;

/**
 * Append-only audit recording use case.
 *
 * Deliberately thin: the audit trail is write-once, so there is no invariant to enforce
 * beyond "the entry as published is the entry as stored". Any transformation would be a
 * chance to falsify the record.
 */
@Service
public class RecordAuditEventHandler implements RecordAuditEventUseCase {

    private final AuditTrailRepository auditTrailRepository;

    public RecordAuditEventHandler(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    @Override
    public void execute(AuditEntry entry) {
        auditTrailRepository.append(entry);
    }
}
