package com.notarist.review.application.query;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.review.domain.valueobject.Reviewer;
import com.notarist.review.domain.valueobject.Role;

import java.util.UUID;

/**
 * The authenticated caller, in the Review context's own language. Built by the controller from the
 * request principal so the application layer never sees an HTTP or auth type.
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

    /** The caller as a domain {@link Reviewer}. */
    public Reviewer asReviewer() {
        return Reviewer.of(userId, role);
    }
}
