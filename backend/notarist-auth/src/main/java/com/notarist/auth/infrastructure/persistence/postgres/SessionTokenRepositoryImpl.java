package com.notarist.auth.infrastructure.persistence.postgres;

import com.notarist.auth.application.port.out.SessionTokenRepository;
import com.notarist.auth.domain.model.Session;
import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.domain.valueobject.SessionId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionTokenRepositoryImpl implements SessionTokenRepository {

    private final JdbcTemplate postgresJdbcTemplate;

    public SessionTokenRepositoryImpl(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
    }

    @Override
    public void save(Session session) {
        postgresJdbcTemplate.update(
                """
                INSERT INTO session_token
                    (session_id, user_id, tenant_id, refresh_token_hash, created_at, expires_at, invalidated)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE
                    SET invalidated = EXCLUDED.invalidated
                """,
                session.getSessionId().value(),
                session.getUserId().value(),
                session.getTenantId(),
                session.getRefreshTokenHash(),
                Timestamp.from(session.getCreatedAt()),
                Timestamp.from(session.getExpiresAt()),
                session.isInvalidated()
        );
    }

    @Override
    public Optional<Session> findByRefreshTokenHash(String refreshTokenHash) {
        var results = postgresJdbcTemplate.query(
                "SELECT session_id, user_id, tenant_id, refresh_token_hash, created_at, expires_at, invalidated FROM session_token WHERE refresh_token_hash = ? AND invalidated = false",
                SESSION_ROW_MAPPER,
                refreshTokenHash
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<Session> findById(SessionId sessionId) {
        var results = postgresJdbcTemplate.query(
                "SELECT session_id, user_id, tenant_id, refresh_token_hash, created_at, expires_at, invalidated FROM session_token WHERE session_id = ?",
                SESSION_ROW_MAPPER,
                sessionId.value()
        );
        return results.stream().findFirst();
    }

    @Override
    public void invalidate(SessionId sessionId) {
        postgresJdbcTemplate.update(
                "UPDATE session_token SET invalidated = true WHERE session_id = ?",
                sessionId.value()
        );
    }

    @Override
    public boolean invalidateIfActive(SessionId sessionId) {
        // Compare-and-set: the WHERE ... AND invalidated = false guard makes this atomic
        // at the row level. Under concurrent refresh of the same token, the first UPDATE
        // flips the row and affects 1 row; every subsequent UPDATE sees invalidated = true
        // and affects 0 rows. Exactly one caller observes a return value of true.
        int rows = postgresJdbcTemplate.update(
                "UPDATE session_token SET invalidated = true WHERE session_id = ? AND invalidated = false",
                sessionId.value()
        );
        return rows == 1;
    }

    @Override
    public void invalidateAllByUserId(PersonId userId) {
        postgresJdbcTemplate.update(
                "UPDATE session_token SET invalidated = true WHERE user_id = ?",
                userId.value()
        );
    }

    private static final RowMapper<Session> SESSION_ROW_MAPPER = (rs, rowNum) -> {
        Session session = new Session(
                new SessionId(UUID.fromString(rs.getString("session_id"))),
                new PersonId(UUID.fromString(rs.getString("user_id"))),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("refresh_token_hash"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
        if (rs.getBoolean("invalidated")) {
            session.invalidate();
        }
        return session;
    };
}
