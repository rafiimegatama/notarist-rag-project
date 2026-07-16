package com.notarist.verification.api.response;

import com.notarist.verification.domain.model.Verification;

/** Progress counters — how the checklist decisions break down. */
public record VerificationProgressResponse(
        int total,
        long passed,
        long failed,
        long notApplicable,
        long manualRequired,
        long remaining,
        boolean checklistComplete
) {
    public static VerificationProgressResponse from(Verification v) {
        return new VerificationProgressResponse(
                v.totalItems(),
                v.passedCount(),
                v.failedCount(),
                v.notApplicableCount(),
                v.manualRequiredCount(),
                v.remainingCount(),
                v.checklistComplete());
    }
}
