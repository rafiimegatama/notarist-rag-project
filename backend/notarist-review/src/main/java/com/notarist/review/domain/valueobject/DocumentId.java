package com.notarist.review.domain.valueobject;

import java.util.UUID;

/**
 * Identity of the reviewed document. Owned by the Document/Ingest contexts; carried here by value so
 * the Review context need not depend on those modules.
 */
public record DocumentId(UUID value) {

    public DocumentId {
        if (value == null) throw new IllegalArgumentException("documentId is required");
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
