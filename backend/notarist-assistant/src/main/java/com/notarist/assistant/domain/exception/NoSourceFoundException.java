package com.notarist.assistant.domain.exception;

import com.notarist.core.domain.exception.NotaristException;

/**
 * Thrown when zero relevant chunks are retrieved — response is aborted.
 * Maps to error code: ASSISTANT_NO_SOURCE_FOUND (HTTP 422).
 */
public class NoSourceFoundException extends NotaristException {

    public NoSourceFoundException(String queryText) {
        super("ASSISTANT_NO_SOURCE_FOUND",
              "Tidak ditemukan dokumen relevan untuk pertanyaan: '" + queryText + "'");
    }
}
