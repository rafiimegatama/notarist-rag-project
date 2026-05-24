package com.notarist.assistant.application.port.out;

import com.notarist.assistant.domain.model.AssistantToken;
import com.notarist.core.domain.valueobject.SessionId;
import reactor.core.publisher.Flux;

import java.util.List;

/** Port for Ollama LLM — :11434. Streaming via Flux<AssistantToken>. */
public interface LlmPort {

    Flux<AssistantToken> streamCompletion(LlmPrompt prompt);

    LlmResponse complete(LlmPrompt prompt);

    record LlmPrompt(
        SessionId sessionId,
        String model,
        String systemInstruction,
        String contextText,
        String userQuery,
        int maxTokens
    ) {}

    record LlmResponse(
        String text,
        int tokensInput,
        int tokensOutput,
        long latencyMs
    ) {}
}
