package com.notarist.auth.domain.model;

import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.domain.valueobject.SessionId;

import java.time.Instant;
import java.util.UUID;

/** Domain model for authenticated session. Refresh token is opaque UUID — not JWT. */
public class Session {

    private final SessionId sessionId;
    private final PersonId userId;
    private final UUID tenantId;
    private final String refreshTokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean invalidated;

    public Session(
            SessionId sessionId,
            PersonId userId,
            UUID tenantId,
            String refreshTokenHash,
            Instant createdAt,
            Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.invalidated = false;
    }

    public void invalidate() {
        this.invalidated = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !invalidated && !isExpired();
    }

    public SessionId getSessionId() { return sessionId; }
    public PersonId getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isInvalidated() { return invalidated; }
}
