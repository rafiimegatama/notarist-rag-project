package com.notarist.assistant.domain.model;

import java.util.List;

/** Value object containing grounding quality metrics for an AI response. */
public record GroundingMetadata(
    float groundingScore,
    GroundingCoverage citationCoverage,
    boolean hallucinationFlagRaised,
    int verifiedCitationCount,
    int unverifiedCitationCount,
    List<String> warningMessages
) {
    public GroundingMetadata {
        warningMessages = warningMessages == null ? List.of() : List.copyOf(warningMessages);
    }

    public static GroundingMetadata highConfidence(int citationCount) {
        return new GroundingMetadata(
            1.0f, GroundingCoverage.FULL, false, citationCount, 0, List.of()
        );
    }
}
