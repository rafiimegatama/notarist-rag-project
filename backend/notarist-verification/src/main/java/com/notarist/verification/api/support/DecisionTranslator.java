package com.notarist.verification.api.support;

import com.notarist.verification.domain.state.Decision;
import org.springframework.stereotype.Component;

/**
 * Translates the decision vocabulary the frontend checklist already speaks
 * ({@code APPROVED | REJECTED | NEEDS_CHECK}) into the domain {@link Decision}, and also accepts the
 * domain names directly. Keeping this at the API boundary lets the existing checklist UI submit
 * decisions unchanged while the domain keeps its full model.
 *
 * <p>{@code REJECTED}/{@code FAIL} carries a reason (comment); when the caller omits it, a non-blank
 * default is supplied so the mandatory-reason rule and the audit trail always hold.
 */
@Component
public class DecisionTranslator {

    private static final String DEFAULT_FAIL_COMMENT = "Failed during verification";

    /** The translated, domain-ready decision plus the (possibly defaulted) comment. */
    public record Translated(Decision decision, String comment) {}

    public Translated translate(String rawDecision, String comment) {
        if (rawDecision == null || rawDecision.isBlank()) {
            throw new IllegalArgumentException("decision is required");
        }
        Decision decision = switch (rawDecision.trim().toUpperCase()) {
            case "APPROVED", "PASS" -> Decision.PASS;
            case "REJECTED", "FAIL" -> Decision.FAIL;
            case "NEEDS_CHECK", "MANUAL_REQUIRED" -> Decision.MANUAL_REQUIRED;
            case "NOT_APPLICABLE", "NA", "N/A" -> Decision.NOT_APPLICABLE;
            default -> throw new IllegalArgumentException("Unknown decision: " + rawDecision);
        };
        String resolved = comment;
        if (decision == Decision.FAIL && (comment == null || comment.isBlank())) {
            resolved = DEFAULT_FAIL_COMMENT;
        }
        return new Translated(decision, resolved);
    }
}
