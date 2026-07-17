package com.notarist.kase.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.kase.domain.state.ApprovalDecision.*;

/** PENDING → APPROVED | REJECTED | EXPIRED. All terminal. No reversal, ever. */
public final class ApprovalStateMachine {

    private ApprovalStateMachine() {}

    private static final Map<ApprovalDecision, Set<ApprovalDecision>> TABLE = build();

    private static Map<ApprovalDecision, Set<ApprovalDecision>> build() {
        Map<ApprovalDecision, Set<ApprovalDecision>> t = new EnumMap<>(ApprovalDecision.class);
        t.put(PENDING,  Set.of(APPROVED, REJECTED, EXPIRED));
        t.put(APPROVED, Set.of());
        t.put(REJECTED, Set.of());
        t.put(EXPIRED,  Set.of());
        return t;
    }

    public static boolean isLegal(ApprovalDecision from, ApprovalDecision to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<ApprovalDecision> allowedTargets(ApprovalDecision from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
