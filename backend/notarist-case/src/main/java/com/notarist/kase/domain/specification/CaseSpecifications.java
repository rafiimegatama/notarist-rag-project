package com.notarist.kase.domain.specification;

import com.notarist.kase.domain.model.Case;
import com.notarist.kase.domain.state.CaseState;

/** The rules about a Case that more than one caller needs to ask about. */
public final class CaseSpecifications {

    private CaseSpecifications() {}

    public static Specification<Case> isTerminal() {
        return of(Case::isTerminal, "the case is already closed");
    }

    public static Specification<Case> isActive() {
        return of(c -> !c.isTerminal(), "the case is closed");
    }

    /** A case awaiting a person, not a worker. Reminders fire only on these. */
    public static Specification<Case> isAwaitingHuman() {
        return of(c -> c.state().isHumanGate(), "the case is not waiting on a person");
    }

    /**
     * May this case be finalized? Only from WAITING_NOTARY_APPROVAL — QC cannot be skipped, because
     * the notary must never be the first person to notice that the NIK is wrong.
     */
    public static Specification<Case> canBeFinalized() {
        return of(c -> c.state() == CaseState.WAITING_NOTARY_APPROVAL,
                "the case is not awaiting notary approval");
    }

    /** A case may only be cancelled before a notary is involved. */
    public static Specification<Case> canBeCancelled() {
        return of(c -> c.canTransitionTo(CaseState.CANCELLED),
                "the case can no longer be cancelled — a notary is already involved");
    }

    public static Specification<Case> needsRepertoriumNumber() {
        return of(c -> c.state() == CaseState.FINALIZED && c.nomorAkta() == null,
                "the case does not need a repertorium number");
    }

    private static Specification<Case> of(java.util.function.Predicate<Case> p, String reason) {
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(Case candidate) { return p.test(candidate); }
            @Override public String reasonUnsatisfied() { return reason; }
        };
    }
}
