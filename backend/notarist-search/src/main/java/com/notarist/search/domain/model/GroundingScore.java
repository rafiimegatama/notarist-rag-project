package com.notarist.search.domain.model;

/**
 * Quantified grounding quality for a retrieval response.
 * Computed before context is handed to any LLM.
 */
public record GroundingScore(
        float retrievalCoverage,
        float citationDensity,
        float semanticAgreement,
        float answerTraceability,
        float overallScore,
        Level level
) {
    public enum Level {
        HIGH,       // >= 0.75
        MEDIUM,     // >= 0.50
        LOW,        // >= 0.25
        UNGROUNDED  // <  0.25
    }

    public static GroundingScore compute(
            float retrievalCoverage,
            float citationDensity,
            float semanticAgreement,
            float answerTraceability) {

        float overall = retrievalCoverage  * 0.35f
                      + citationDensity    * 0.25f
                      + semanticAgreement  * 0.25f
                      + answerTraceability * 0.15f;

        Level level;
        if      (overall >= 0.75f) level = Level.HIGH;
        else if (overall >= 0.50f) level = Level.MEDIUM;
        else if (overall >= 0.25f) level = Level.LOW;
        else                       level = Level.UNGROUNDED;

        return new GroundingScore(
                retrievalCoverage, citationDensity,
                semanticAgreement, answerTraceability, overall, level);
    }

    public static GroundingScore ungrounded() {
        return new GroundingScore(0f, 0f, 0f, 0f, 0f, Level.UNGROUNDED);
    }
}
