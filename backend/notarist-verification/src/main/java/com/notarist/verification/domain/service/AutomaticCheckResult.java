package com.notarist.verification.domain.service;

import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.ChecklistCategory;

/** The outcome of one automatic check: which category, its title, the computed decision and why. */
public record AutomaticCheckResult(
        ChecklistCategory category,
        String title,
        boolean mandatory,
        Decision decision,
        String comment
) {}
