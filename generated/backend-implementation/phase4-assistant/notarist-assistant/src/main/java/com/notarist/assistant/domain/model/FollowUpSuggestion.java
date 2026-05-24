package com.notarist.assistant.domain.model;

/** A suggested follow-up question generated after an assistant response. */
public record FollowUpSuggestion(
        String questionText,
        String intent,
        int priority
) {}
