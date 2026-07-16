package com.notarist.kase.domain.valueobject;

import com.notarist.core.domain.valueobject.DocumentId;

/**
 * A reference to a document that lives in the Document aggregate — an ID and the part it plays here.
 *
 * <p>A Bundle holds these, never {@code DocumentLegal} objects. The Document aggregate is NOT
 * duplicated, NOT extended, and NOT owned by the Case context. Five ingest workers mutate document
 * state concurrently; pulling documents inside this aggregate would force every OCR completion to
 * take a lock on a business case.
 */
public record DocumentRef(DocumentId documentId, String roleInBundle) {

    public DocumentRef {
        if (documentId == null) throw new IllegalArgumentException("documentId must not be null");
    }

    public static DocumentRef of(DocumentId documentId, String roleInBundle) {
        return new DocumentRef(documentId, roleInBundle);
    }
}
