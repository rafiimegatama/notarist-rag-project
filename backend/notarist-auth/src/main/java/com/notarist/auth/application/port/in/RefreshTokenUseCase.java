package com.notarist.auth.application.port.in;

import com.notarist.auth.application.command.RefreshTokenCommand;
import com.notarist.auth.api.response.TokenResponse;
import com.notarist.core.application.usecase.UseCase;

public interface RefreshTokenUseCase extends UseCase<RefreshTokenCommand, TokenResponse> {
    TokenResponse execute(RefreshTokenCommand command);
}
