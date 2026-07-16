package com.notarist.kase.domain.specification;

import com.notarist.kase.domain.model.Approval;
import com.notarist.kase.domain.valueobject.Actor;

public final class ApprovalSpecifications {

    private ApprovalSpecifications() {}

    public static Specification<Approval> isPending() {
        return of(Approval::isPending, "the approval has already been decided");
    }

    /** Can THIS actor decide it? Combines authority and four-eyes — the two rules that matter. */
    public static Specification<Approval> decidableBy(Actor actor) {
        return new Specification<>() {
            @Override
            public boolean isSatisfiedBy(Approval a) {
                if (!a.isPending()) return false;
                if (actor == null || actor.isSystem()) return false;
                if (!a.approvalType().mayBeDecidedBy(actor.role())) return false;
                boolean selfApproval = a.approvalType().requiresFourEyes()
                        && a.submittedBy() != null
                        && a.submittedBy().equals(actor.userId());
                return !selfApproval;
            }

            @Override
            public String reasonUnsatisfied() {
                return "this actor may not decide this approval "
                        + "(wrong role, already decided, or would be approving their own work)";
            }
        };
    }

    private static Specification<Approval> of(java.util.function.Predicate<Approval> p, String reason) {
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(Approval candidate) { return p.test(candidate); }
            @Override public String reasonUnsatisfied() { return reason; }
        };
    }
}
