package com.notarist.auth.domain.service;

import com.notarist.auth.domain.model.Session;
import com.notarist.core.domain.valueobject.PersonId;
import com.notarist.core.domain.valueobject.SessionId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Domain service for creating opaque refresh tokens and their SHA-256 hashes. */
public final class RefreshTokenFactory {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private RefreshTokenFactory() {}

    public static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String opaqueToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(opaqueToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static Session createSession(
            PersonId userId,
            UUID tenantId,
            String opaqueToken,
            long refreshTokenTtlSeconds) {
        return new Session(
                new SessionId(UUID.randomUUID()),
                userId,
                tenantId,
                hashToken(opaqueToken),
                Instant.now(),
                Instant.now().plusSeconds(refreshTokenTtlSeconds)
        );
    }
}
