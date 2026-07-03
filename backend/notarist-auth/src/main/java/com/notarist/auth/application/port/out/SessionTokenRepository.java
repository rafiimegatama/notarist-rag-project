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
    void invalidateAllByUserId(PersonId userId);
}
