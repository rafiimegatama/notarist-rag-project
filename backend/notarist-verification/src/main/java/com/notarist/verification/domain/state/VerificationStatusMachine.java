package com.notarist.verification.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.verification.domain.state.VerificationStatus.*;

/**
 * The authoritative verification-status transition table. Nothing that is not listed here is possible.
 *
 * <p>Used only from inside the {@code Verification} aggregate — it is package-visible behaviour, not a
 * helper a service may call to "check first and then mutate". The aggregate has no public status
 * setter, so this table is the single gate through which status changes pass.
 */
public final class VerificationStatusMachine {

    private VerificationStatusMachine() {}

    private static final Map<VerificationStatus, Set<VerificationStatus>> TABLE = build();

    private static Map<VerificationStatus, Set<VerificationStatus>> build() {
        Map<VerificationStatus, Set<VerificationStatus>> t = new EnumMap<>(VerificationStatus.class);
        t.put(PENDING, Set.of(UNDER_VERIFICATION));
        t.put(UNDER_VERIFICATION, Set.of(VERIFIED, FAILED));
        t.put(VERIFIED, Set.of(UNDER_VERIFICATION));   // return for rework
        t.put(FAILED, Set.of(UNDER_VERIFICATION));     // reopen after failure
        return t;
    }

    public static boolean isLegal(VerificationStatus from, VerificationStatus to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    /** Whether moving from → to is a "return" (an outcome being reopened for rework). */
    public static boolean isReturn(VerificationStatus from, VerificationStatus to) {
        return from.isOutcome() && to == UNDER_VERIFICATION;
    }

    /** Every status reachable from {@code from}. Lets the UI offer only lawful actions. */
    public static Set<VerificationStatus> allowedTargets(VerificationStatus from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
