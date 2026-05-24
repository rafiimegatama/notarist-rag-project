package com.notarist.core.domain.exception;

/** Base exception for all domain exceptions in NOTARIST RAG Platform. */
public abstract class NotaristException extends RuntimeException {

    private final String errorCode;

    protected NotaristException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected NotaristException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
