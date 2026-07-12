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

import java.util.UUID;

@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/auth")
@Tag(name = "Authentication", description = "JWT authentication and session management")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final InvalidateSessionUseCase invalidateSessionUseCase;

    public AuthController(
            AuthenticateUserUseCase authenticateUserUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            InvalidateSessionUseCase invalidateSessionUseCase) {
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.invalidateSessionUseCase = invalidateSessionUseCase;
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

    @PostMapping("/logout")
    @Operation(summary = "Invalidate current session", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestParam UUID sessionId,
            @RequestParam(required = false) String jti,
            @RequestParam(required = false, defaultValue = "0") long remainingTtlSeconds,
            HttpServletRequest httpRequest) {

        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));

        CorrelationId correlationId = extractCorrelationId(httpRequest);
        LogoutCommand command = new LogoutCommand(
                new SessionId(sessionId),
                principal.userId(),
                jti,
                remainingTtlSeconds,
                correlationId
        );

        invalidateSessionUseCase.execute(command);
        return ResponseEntity.ok(
                ApiResponse.success(ApiMeta.of(correlationId.value()), null));
    }

    private CorrelationId extractCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        return (header != null && !header.isBlank())
                ? CorrelationId.of(header)
                : CorrelationId.generate();
    }
}
