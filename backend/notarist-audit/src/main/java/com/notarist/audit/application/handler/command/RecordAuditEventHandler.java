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
 *
 * <p><b>How the record commits independently.</b> The append runs on the dedicated, autocommit
 * audit datasource ({@code AuditConnectionConfig} / {@code auditJdbcTemplate}), which is separate
 * from the business pool and enlisted in no caller transaction. The single INSERT therefore commits
 * immediately, independently of the caller's outcome — the regulated access trail must not vanish
 * when the operation that triggered it is rejected: {@code loadForCaller(...)} audits
 * {@code SECURITY_ACCESS_DENIED} and then throws, rolling its transaction back, yet the audit row
 * has already committed on the audit connection. This replaces the former {@code REQUIRES_NEW},
 * which achieved the same independence by suspending the caller's transaction and drawing a second
 * connection from the shared business pool — the connection-amplification that
 * {@code AuditRequiresNewPoolExhaustionIT} proved could deadlock under concurrency.
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
