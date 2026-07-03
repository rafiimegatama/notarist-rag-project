package com.notarist.auth.application.service;

import com.notarist.auth.domain.model.Role;
import com.notarist.auth.domain.model.User;
import com.notarist.core.domain.valueobject.CorrelationId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Issues and validates RS256 JWT access tokens using JJWT 0.12.x. */
@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${notarist.auth.jwt.private-key-path}") String privateKeyPath,
            @Value("${notarist.auth.jwt.public-key-path}") String publicKeyPath,
            @Value("${notarist.auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT RSA key pair from: " + privateKeyPath, e);
        }
    }

    public String issueAccessToken(User user, CorrelationId correlationId) {
        Instant now = Instant.now();
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUserId().value().toString())
                .issuer("notarist-auth")
                .audience().add("notarist-api").and()
                .claim("username", user.getUsername())
                .claim("fullName", user.getFullName())
                .claim("roles", roleNames)
                .claim("tenantId", user.getTenantId().toString())
                .claim("correlationId", correlationId.value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateAndParseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new InvalidTokenException("JWT validation failed: " + e.getMessage(), e);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private PrivateKey loadPrivateKey(String path)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = Files.readString(Paths.get(path))
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey loadPublicKey(String path)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = Files.readString(Paths.get(path))
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
