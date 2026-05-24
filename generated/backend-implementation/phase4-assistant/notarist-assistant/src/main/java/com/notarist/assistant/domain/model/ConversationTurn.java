package com.notarist.assistant.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One question-answer pair stored in conversation memory per session. */
public record ConversationTurn(
        UUID turnId,
        UUID sessionId,
        UUID tenantId,
        UUID userId,
        String userQuery,
        String assistantAnswer,
        AnswerConfidence confidence,
        boolean hallucinationWarning,
        String promptVersion,
        UUID traceId,
        Instant timestamp
) {
    public static ConversationTurn create(
            UUID sessionId, UUID tenantId, UUID userId,
            String userQuery, String assistantAnswer,
            AnswerConfidence confidence, boolean hallucinationWarning,
            String promptVersion, UUID traceId) {
        return new ConversationTurn(
                UUID.randomUUID(), sessionId, tenantId, userId,
                userQuery, assistantAnswer,
                confidence, hallucinationWarning,
                promptVersion, traceId, Instant.now());
    }
}
