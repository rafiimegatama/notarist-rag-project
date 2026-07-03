package com.notarist.assistant.application.port.in;

import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.application.command.AssistantCommand;

public interface AssistantUseCase {
    AssistantResponse ask(AssistantCommand command);
}
