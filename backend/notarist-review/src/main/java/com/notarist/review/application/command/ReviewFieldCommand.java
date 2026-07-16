package com.notarist.review.application.command;

import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.state.FieldDecision;
import com.notarist.review.domain.valueobject.DocumentId;
import com.notarist.review.domain.valueobject.FieldId;

/**
 * Update one reviewed field. {@code correctedValue} is only meaningful for {@code CORRECTED};
 * {@code reason} is only meaningful for {@code REJECTED} (and is mandatory there).
 */
public record ReviewFieldCommand(
        DocumentId documentId,
        FieldId fieldId,
        FieldDecision decision,
        String correctedValue,
        String reason,
        CallerContext caller
) {
    public ReviewFieldCommand {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (fieldId == null) throw new IllegalArgumentException("fieldId is required");
        if (decision == null) throw new IllegalArgumentException("decision is required");
        if (caller == null) throw new IllegalArgumentException("caller is required");
    }
}
