package com.notarist.review.application.command;

import com.notarist.review.application.query.CallerContext;
import com.notarist.review.domain.valueobject.AuthorityType;

import java.util.List;
import java.util.UUID;

/**
 * Provisions the review landing rows for a document whose OCR extraction has completed. Not exposed as
 * a REST endpoint — OCR inference lives outside this module. It exists so a future OCR-completion
 * listener (or a test) can seed a review without reaching into the aggregate directly.
 */
public record InitializeReviewCommand(
        UUID documentId,
        UUID tenantId,
        String documentName,
        int pageCount,
        boolean stampDetected,
        boolean signatureDetected,
        double overallConfidence,
        List<FieldSpec> fields,
        List<AuthoritySpec> authorityItems,
        CallerContext caller
) {
    public InitializeReviewCommand {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        fields = fields == null ? List.of() : List.copyOf(fields);
        authorityItems = authorityItems == null ? List.of() : List.copyOf(authorityItems);
    }

    /** One extracted field, with its relative bounding box on the page. */
    public record FieldSpec(
            String fieldName,
            String displayLabel,
            String extractedValue,
            double confidence,
            int page,
            double x,
            double y,
            double width,
            double height
    ) {}

    /** One read-only authority extraction. */
    public record AuthoritySpec(
            AuthorityType type,
            String roleLabel,
            String personName,
            String content,
            Double confidence
    ) {}
}
