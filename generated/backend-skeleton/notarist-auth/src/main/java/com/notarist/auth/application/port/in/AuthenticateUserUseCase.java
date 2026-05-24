package com.notarist.auth.application.port.in;

import com.notarist.auth.application.command.AuthenticateCommand;
import com.notarist.auth.api.response.TokenResponse;
import com.notarist.core.application.usecase.UseCase;

public interface AuthenticateUserUseCase extends UseCase<AuthenticateCommand, TokenResponse> {
    TokenResponse execute(AuthenticateCommand command);
}
