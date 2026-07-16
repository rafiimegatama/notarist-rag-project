package com.notarist.kase.application.command;

import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseId;

/**
 * Request to move a case to {@code targetState}. The reason is mandatory only for ROLLBACK/CANCEL
 * edges — the aggregate enforces that, not this command.
 */
public record ChangeCaseStatusCommand(
        CaseId caseId,
        CaseState targetState,
        String reason,
        CallerContext caller
) {
    public ChangeCaseStatusCommand {
        if (caseId == null) throw new IllegalArgumentException("caseId is required");
        if (targetState == null) throw new IllegalArgumentException("targetState is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
