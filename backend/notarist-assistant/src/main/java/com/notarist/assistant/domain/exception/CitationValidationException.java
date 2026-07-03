package com.notarist.assistant.domain.exception;

import com.notarist.core.domain.exception.NotaristException;

/**
 * Thrown when a citation references a non-existent chunk — indicates internal data integrity issue.
 * Maps to error code: ASSISTANT_CITATION_INVALID (HTTP 500 — internal error).
 */
public class CitationValidationException extends NotaristException {

    public CitationValidationException(String chunkId) {
        super("ASSISTANT_CITATION_INVALID",
              "Citation references non-existent chunkId: '" + chunkId + "'");
    }
}
