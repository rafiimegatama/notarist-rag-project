package com.notarist.audit.application.handler.command;

import com.notarist.audit.application.port.in.RecordAuditEventUseCase;
import com.notarist.audit.application.port.out.AuditTrailRepository;
import com.notarist.audit.domain.model.AuditEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit recording use case.
 *
 * Deliberately thin: the audit trail is write-once, so there is no invariant to enforce
 * beyond "the entry as published is the entry as stored". Any transformation would be a
 * chance to falsify the record.
 *
 * <p><b>Why REQUIRES_NEW.</b> Since Oracle was replaced by PostgreSQL the audit table shares the one
 * datasource with the business tables ({@code PostgresConnectionConfig}: "there is no second
 * database"), so the audit INSERT — written synchronously on the caller's thread — would otherwise
 * enlist in whatever transaction the caller is running and roll back with it. The regulated access
 * trail must not vanish when the operation that triggered it is rejected: {@code loadForCaller(...)}
 * audits {@code SECURITY_ACCESS_DENIED} and then throws a not-found exception, rolling that
 * transaction back. A dedicated transaction commits the record independently of the caller's outcome,
 * which is exactly the "commits independently" behaviour the audit listener already assumes.
 */
@Service
public class RecordAuditEventHandler implements RecordAuditEventUseCase {

    private final AuditTrailRepository auditTrailRepository;

    public RecordAuditEventHandler(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(AuditEntry entry) {
        auditTrailRepository.append(entry);
    }
}
