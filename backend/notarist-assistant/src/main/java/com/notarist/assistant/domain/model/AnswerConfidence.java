package com.notarist.assistant.domain.model;

/**
 * Confidence level of an assistant answer, derived from retrieval grounding score
 * and applied safety mode. Independent of Phase 3's GroundingScore.Level to avoid
 * cross-module coupling on search internals.
 *
 * Mapping from raw grounding score (STRICT mode):
 *   >= 0.75 → HIGH
 *   >= 0.50 → MEDIUM
 *   >= 0.25 → LOW
 *   <  0.25 → INSUFFICIENT
 */
public enum AnswerConfidence {

    /** Strongly grounded; answer is well-supported by retrieved documents. */
    HIGH,

    /** Adequately grounded; minor gaps acceptable with explicit uncertainty language. */
    MEDIUM,

    /** Weakly grounded; answer must include prominent caution warning. */
    LOW,

    /** Not grounded; fallback message replaces answer; LLM skipped in STRICT mode. */
    INSUFFICIENT
}
