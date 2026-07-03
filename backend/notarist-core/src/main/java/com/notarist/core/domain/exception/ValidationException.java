package com.notarist.core.domain.exception;

import java.util.List;

public class ValidationException extends NotaristException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String field, String issue) {
        super("VALIDATION_FIELD_INVALID_FORMAT",
              "Validation failed on field '" + field + "': " + issue);
        this.fieldErrors = List.of(new FieldError(field, issue));
    }

    public ValidationException(List<FieldError> fieldErrors) {
        super("VALIDATION_FIELD_INVALID_FORMAT", "Validation failed on multiple fields");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String issue) {}
}
