package com.notarist.kase.domain.state;

import java.util.Set;

/**
 * The business lifecycle of a Case — human workflow, not machine pipeline.
 *
 * <p>{@code OCR_RUNNING} here is a <b>derived summary</b> ("at least one document in this case has not
 * finished its pipeline"), not a driver. The Case observes the ingestion pipeline; it never advances
 * it, and the pipeline never writes CaseState. This is the altitude rule that keeps the fourth status
 * vocabulary in this codebase from collapsing into the other three ({@code DocumentStatus},
 * {@code PipelineStatus}, {@code PipelineStage}).
 */
public enum CaseState {

    CASE_CREATED,
    UPLOADING,
    OCR_RUNNING,
    OCR_FAILED,
    FIELD_EXTRACTION,
    WAITING_VERIFICATION,
    VERIFIED,
    GENERATING_DRAFT,
    DRAFT_FAILED,
    WAITING_QC,
    QC_FAILED,
    QC_APPROVED,
    WAITING_NOTARY_APPROVAL,
    FINALIZED,
    DELIVERED,
    ARCHIVED,
    CANCELLED;

    private static final Set<CaseState> TERMINAL = Set.of(ARCHIVED, CANCELLED);

    /** States where the system is waiting on a person. Reminders fire only on these. */
    private static final Set<CaseState> HUMAN_GATE =
            Set.of(WAITING_VERIFICATION, WAITING_QC, WAITING_NOTARY_APPROVAL);

    /** Failure states that can re-enter the same stage. */
    private static final Set<CaseState> RETRYABLE = Set.of(OCR_FAILED, DRAFT_FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** A case in a human gate needs a person, not a worker. */
    public boolean isHumanGate() {
        return HUMAN_GATE.contains(this);
    }

    public boolean isRetryable() {
        return RETRYABLE.contains(this);
    }
}
