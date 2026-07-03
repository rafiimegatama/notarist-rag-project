package com.notarist.assistant.domain.model;

import com.notarist.core.domain.valueobject.ChunkId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.SessionId;

import java.util.UUID;

/**
 * Value object representing a verified document citation in an AI response.
 * Immutable after creation — once verified, fields do not change.
 * Citation is audit-logged per STEP 7.5 contract.
 */
public record Citation(
    UUID citationId,
    SessionId sessionId,
    int citationIndex,
    ChunkId chunkId,
    DocumentId documentId,
    String documentTitle,
    String nomorAkta,
    int chunkIndex,
    String excerpt,
    Integer pageNumber,
    String sectionTitle,
    float confidence,
    boolean verified,
    String verificationMethod
) {
    public Citation {
        if (citationIndex < 1) {
            throw new IllegalArgumentException("citationIndex must be >= 1 (1-based)");
        }
        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt must not be blank — citation requires source text");
        }
    }
}
