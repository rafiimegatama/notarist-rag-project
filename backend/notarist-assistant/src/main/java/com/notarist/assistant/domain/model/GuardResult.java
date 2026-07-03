package com.notarist.assistant.domain.model;

import java.util.List;

/**
 * Result of HallucinationGuard evaluation.
 * passed=true means no action required.
 * downgraded=true means the response text was replaced with a fallback message.
 */
public record GuardResult(
        boolean passed,
        boolean downgraded,
        List<String> warnings,
        String downgradeReason,
        AnswerConfidence adjustedConfidence
) {
    public static GuardResult pass(AnswerConfidence confidence) {
        return new GuardResult(true, false, List.of(), null, confidence);
    }

    public static GuardResult passWithWarnings(List<String> warnings, AnswerConfidence confidence) {
        return new GuardResult(true, false, List.copyOf(warnings), null, confidence);
    }

    public static GuardResult downgrade(String reason, List<String> warnings) {
        return new GuardResult(false, true, List.copyOf(warnings), reason, AnswerConfidence.INSUFFICIENT);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
