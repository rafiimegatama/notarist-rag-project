package com.notarist.audit.application.port.in;

import com.notarist.audit.domain.model.AuditEntry;
import com.notarist.core.application.usecase.CommandUseCase;

/** Append-only audit recording. Failure to write = business operation failure. */
public interface RecordAuditEventUseCase extends CommandUseCase<AuditEntry> {
    void execute(AuditEntry entry);
}
