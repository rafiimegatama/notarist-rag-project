package com.notarist.assistant.domain.model;

public enum AssistantSafetyMode {

    /** No unsupported inference; every claim requires a citation; LLM skipped if INSUFFICIENT grounding. */
    STRICT,

    /** Reasoned inference permitted with explicit uncertainty markers; LOW grounding triggers warnings only. */
    BALANCED,

    /** Broader synthesis allowed; all inferences explicitly flagged; grounding check advisory only. */
    EXPLORATORY
}
