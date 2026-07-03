package com.notarist.assistant.application.port.in;

import com.notarist.assistant.application.command.SubmitQueryCommand;
import com.notarist.assistant.domain.model.AssistantToken;
import reactor.core.publisher.Flux;

/**
 * Use case for SSE streaming AI response.
 * Returns a Flux that emits AssistantToken events one by one.
 * Subscription triggers retrieval + LLM pipeline.
 */
public interface StreamAssistantUseCase {
    Flux<AssistantToken> execute(SubmitQueryCommand command);
}
