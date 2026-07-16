package com.notarist.auth.api.rest;

import com.notarist.auth.application.command.AuthenticateCommand;
import com.notarist.auth.application.command.LogoutCommand;
import com.notarist.auth.application.command.RefreshTokenCommand;
import com.notarist.auth.application.port.in.AuthenticateUserUseCase;
import com.notarist.auth.application.port.in.InvalidateSessionUseCase;
import com.notarist.auth.application.port.in.RefreshTokenUseCase;
import com.notarist.auth.api.request.LoginRequest;
import com.notarist.auth.api.request.RefreshTokenRequest;
import com.notarist.auth.api.response.TokenResponse;
import com.notarist.auth.application.service.JwtService;
import io.jsonwebtoken.Claims;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.SessionId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.core.util.NotaristConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/auth")
@Tag(name = "Authentication", description = "JWT authentication and session management")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final InvalidateSessionUseCase invalidateSessionUseCase;
    private final JwtService jwtService;

    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            InvalidateSessionUseCase invalidateSessionUseCase,
            JwtService jwtService) {
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.invalidateSessionUseCase = invalidateSessionUseCase;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain JWT access token")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        AuthenticateCommand command = new AuthenticateCommand(
                request.username(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                correlationId
        );

        TokenResponse tokenResponse = authenticateUserUseCase.execute(command);
        return ResponseEntity.ok(
                ApiResponse.success(ApiMeta.of(correlationId.value()), tokenResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        RefreshTokenCommand command = new RefreshTokenCommand(
                request.refreshToken(),
                httpRequest.getRemoteAddr(),
                correlationId
        );

        TokenResponse tokenResponse = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok(
                ApiResponse.success(ApiMeta.of(correlationId.value()), tokenResponse));
    }

    /**
     * Invalidates the caller's session and revokes the access token it was called with.
     *
     * <p>The revoked jti and its remaining validity are taken from the CALLER'S OWN bearer token,
     * never from the request. They were previously {@code @RequestParam}s handed straight to the
     * deny-list without an ownership check, which meant any authenticated caller could revoke a
     * token whose jti they knew, and could insert arbitrary jti values with an arbitrary
     * (far-future) TTL — the purge only reclaims *expired* rows, so the deny-list could be grown
     * without bound. It also meant a client that simply omitted {@code jti} got a logout that
     * revoked nothing and left its access token valid until natural expiry.
     */
    @PostMapping("/logout")
    @Operation(summary = "Invalidate current session", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestParam UUID sessionId,
            HttpServletRequest httpRequest) {

        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));

        // The filter already validated this token to authenticate the request; re-parsing it here
        // is what binds the revocation to the caller rather than to whatever they typed.
        Claims callerClaims = jwtService.validateAndParseClaims(bearerToken(httpRequest));
        String callerJti = callerClaims.getId();
        long remainingTtlSeconds = remainingValiditySeconds(callerClaims);

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        LogoutCommand command = new LogoutCommand(
                new SessionId(sessionId),
                principal.userId(),
                callerJti,
                remainingTtlSeconds,
                correlationId
        );

        invalidateSessionUseCase.execute(command);
        return ResponseEntity.ok(
                ApiResponse.success(ApiMeta.of(correlationId.value()), null));
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // Unreachable via the security chain (logout is an authenticated endpoint), but a
            // logout that cannot identify its own token must fail rather than revoke nothing.
            throw new IllegalStateException("Missing bearer token on an authenticated request");
        }
        return header.substring(7);
    }

    /** Remaining life of the caller's access token; never negative. */
    private static long remainingValiditySeconds(Claims claims) {
        if (claims.getExpiration() == null) return 0L;
        long seconds = Duration.between(Instant.now(), claims.getExpiration().toInstant()).getSeconds();
        return Math.max(seconds, 0L);
    }

    private CorrelationId extractCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        return (header != null && !header.isBlank())
                ? CorrelationId.of(header)
                : CorrelationId.generate();
    }
}
