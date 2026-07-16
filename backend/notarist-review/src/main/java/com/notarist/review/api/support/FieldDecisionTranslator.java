package com.notarist.review.api.support;

import com.notarist.review.domain.state.FieldDecision;
import org.springframework.stereotype.Component;

/**
 * Translates the decision vocabulary the frontend already speaks
 * ({@code APPROVED | REJECTED | NEEDS_CHECK}, optionally with an edited value) into the richer domain
 * {@link FieldDecision}, and also accepts the domain names directly. Keeping this at the API boundary
 * lets the existing OcrReviewScreen submit decisions unchanged while the domain keeps its full model.
 *
 * <p>Rules:
 * <ul>
 *   <li>A supplied (non-blank) value is a correction → {@code CORRECTED}, unless the decision is a
 *       rejection.</li>
 *   <li>{@code APPROVED} with no value → {@code MANUAL_ACCEPTED} (a human accepted the extraction).</li>
 *   <li>{@code NEEDS_CHECK}/{@code NEEDS_REVIEW} with no value → {@code NEEDS_REVIEW}.</li>
 *   <li>{@code REJECTED} carries the reason; when the caller omits it, a non-blank default is supplied
 *       so the mandatory-reason rule and the audit trail always hold.</li>
 * </ul>
 */
@Component
public class FieldDecisionTranslator {

    private static final String DEFAULT_REJECTION_REASON = "Rejected during OCR review";

    /** The translated, domain-ready decision. */
    public record Translated(FieldDecision decision, String correctedValue, String reason) {}

    public Translated translate(String rawDecision, String value, String reason) {
        if (rawDecision == null || rawDecision.isBlank()) {
            throw new IllegalArgumentException("decision is required");
        }
        String norm = rawDecision.trim().toUpperCase();
        boolean hasValue = value != null && !value.isBlank();

        return switch (norm) {
            case "REJECTED" -> new Translated(FieldDecision.REJECTED, null, reasonOrDefault(reason));
            case "AUTO_ACCEPTED" -> new Translated(FieldDecision.AUTO_ACCEPTED, null, null);
            case "MANUAL_ACCEPTED" -> new Translated(FieldDecision.MANUAL_ACCEPTED, null, null);
            case "CORRECTED" -> new Translated(FieldDecision.CORRECTED, requireValue(value), null);
            case "APPROVED" -> hasValue
                    ? new Translated(FieldDecision.CORRECTED, value, null)
                    : new Translated(FieldDecision.MANUAL_ACCEPTED, null, null);
            case "NEEDS_CHECK", "NEEDS_REVIEW" -> hasValue
                    ? new Translated(FieldDecision.CORRECTED, value, null)
                    : new Translated(FieldDecision.NEEDS_REVIEW, null, null);
            default -> throw new IllegalArgumentException("Unknown decision: " + rawDecision);
        };
    }

    private String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a CORRECTED decision requires a non-blank value");
        }
        return value;
    }

    private String reasonOrDefault(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : DEFAULT_REJECTION_REASON;
    }
}
