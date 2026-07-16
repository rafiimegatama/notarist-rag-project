package com.notarist.verification.domain.state;

/** Lifecycle of a single checklist item: undecided, then settled once a decision is recorded. */
public enum ChecklistItemStatus {
    PENDING,
    COMPLETED
}
