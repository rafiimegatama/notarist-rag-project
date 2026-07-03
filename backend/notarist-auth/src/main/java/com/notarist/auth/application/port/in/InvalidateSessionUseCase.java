package com.notarist.auth.application.port.in;

import com.notarist.auth.application.command.LogoutCommand;
import com.notarist.core.application.usecase.CommandUseCase;

public interface InvalidateSessionUseCase extends CommandUseCase<LogoutCommand> {
    void execute(LogoutCommand command);
}
