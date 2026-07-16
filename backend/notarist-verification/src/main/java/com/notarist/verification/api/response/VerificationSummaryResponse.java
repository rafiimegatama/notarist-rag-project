package com.notarist.verification.api.response;

import com.notarist.verification.domain.model.ChecklistItem;
import com.notarist.verification.domain.model.Verification;

import java.util.UUID;

/**
 * Read model for {@code GET /verification/summary}, also embedded in the full verification response.
 * Says at a glance whether the verification can be completed and what is standing in the way.
 */
public record VerificationSummaryResponse(
        UUID bundleId,
        UUID verificationId,
        String status,
        VerificationProgressResponse progress,
        boolean completable,
        long blocking,
        long mandatoryOutstanding
) {
    public static VerificationSummaryResponse from(Verification v) {
        long mandatoryOutstanding = v.items().stream()
                .filter(ChecklistItem::mandatory)
                .filter(i -> i.decision() == null || !i.decision().isAcceptable())
                .count();
        return new VerificationSummaryResponse(
                v.bundleId(),
                v.verificationId().value(),
                v.status().name(),
                VerificationProgressResponse.from(v),
                v.completable(),
                v.manualRequiredCount(),
                mandatoryOutstanding);
    }
}
