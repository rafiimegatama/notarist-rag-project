package com.notarist.core.domain.exception;

import com.notarist.core.domain.valueobject.DocumentId;

public class DocumentNotFoundException extends NotaristException {

    public DocumentNotFoundException(DocumentId documentId) {
        super("DOCUMENT_NOT_FOUND",
              "Document dengan ID '" + documentId + "' tidak ditemukan.");
    }

    public DocumentNotFoundException(String message) {
        super("DOCUMENT_NOT_FOUND", message);
    }
}
