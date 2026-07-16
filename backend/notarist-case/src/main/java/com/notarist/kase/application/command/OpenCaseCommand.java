package com.notarist.kase.application.command;

import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.valueobject.CaseType;

import java.util.UUID;

/**
 * Request to open a new case. The caller carries identity/tenant/authority; the aggregate decides
 * whether that actor may open one (a human, never SYSTEM).
 */
public record OpenCaseCommand(
        String caseNumber,
        CaseType caseType,
        UUID assignedNotarisId,
        CallerContext caller
) {
    public OpenCaseCommand {
        if (caseNumber == null || caseNumber.isBlank())
            throw new IllegalArgumentException("caseNumber is required");
        if (caseType == null) throw new IllegalArgumentException("caseType is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
