package com.notarist.assistant.api.response;

import java.util.UUID;

/** SSE event: citation — citation reference assembled pre-LLM. */
public record SseCitationEvent(
    UUID citationId,
    int citationIndex,
    UUID chunkId,
    UUID documentId,
    String documentTitle,
    String nomorAkta,
    String excerpt,
    Integer pageNumber,
    String sectionTitle,
    float confidence,
    boolean verified
) {
    public static final String EVENT_TYPE = "citation";
}
