package com.notarist.assistant.domain.model;

import com.notarist.core.domain.valueobject.SessionId;

/** Single streaming token emitted by LLM. Maps to SSE event: token. */
public record AssistantToken(
    SessionId sessionId,
    String token,
    int index
) {}
