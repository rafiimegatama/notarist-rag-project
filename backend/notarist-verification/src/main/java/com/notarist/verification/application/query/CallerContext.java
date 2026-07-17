package com.notarist.verification.application.query;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.verification.domain.valueobject.Reviewer;
import com.notarist.verification.domain.valueobject.Role;

import java.util.UUID;

/**
 * The authenticated caller, in the Verification context's own language. Built by the controller from
 * the request principal so the application layer never sees an HTTP or auth type.
 */
public record CallerContext(
        UUID userId,
        UUID tenantId,
        Role role,
        CorrelationId correlationId
) {
    public CallerContext {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (role == null) throw new IllegalArgumentException("role is required");
        if (correlationId == null) throw new IllegalArgumentException("correlationId is required");
    }

    public Reviewer asReviewer() {
        return Reviewer.of(userId, role);
    }
}
