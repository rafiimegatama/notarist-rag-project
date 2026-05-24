package com.notarist.assistant.domain.model;

public enum GroundingCoverage {
    /** All claims have at least one citation. */
    FULL,
    /** Some claims uncited — warning raised. */
    PARTIAL,
    /** No citations — response blocked before reaching client. */
    NONE
}
