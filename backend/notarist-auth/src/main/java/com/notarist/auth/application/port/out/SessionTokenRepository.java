package com.notarist.auth.application.port.out;

import com.notarist.auth.domain.model.Session;
import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.domain.valueobject.SessionId;

import java.util.Optional;

public interface SessionTokenRepository {
    void save(Session session);
    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);
    Optional<Session> findById(SessionId sessionId);
    void invalidate(SessionId sessionId);

    /**
     * Atomically invalidates the session only if it is still active.
     * Returns {@code true} iff this call transitioned the row from active to invalidated
     * (i.e. the caller "won" the row). Used to make refresh-token rotation race-safe:
     * exactly one concurrent refresh of the same token can win, the rest get {@code false}.
     */
    boolean invalidateIfActive(SessionId sessionId);

    void invalidateAllByUserId(PersonId userId);
}
