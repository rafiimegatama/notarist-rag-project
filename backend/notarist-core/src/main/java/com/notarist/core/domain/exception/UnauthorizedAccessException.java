package com.notarist.core.domain.exception;

public class UnauthorizedAccessException extends NotaristException {

    public UnauthorizedAccessException(String reason) {
        super("ACCESS_INSUFFICIENT_ROLE", reason);
    }

    public UnauthorizedAccessException(String errorCode, String message) {
        super(errorCode, message);
    }
}
