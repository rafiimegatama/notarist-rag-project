package com.notarist.kase.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.kase.domain.state.BundleStatus.*;

/**
 * OPEN ⇄ COMPLETE → LOCKED.
 *
 * <p>The only cycle in the whole context, and it is intentional: attaching a document completes a
 * bundle, detaching one reopens it. Once LOCKED, nothing.
 */
public final class BundleStateMachine {

    private BundleStateMachine() {}

    private static final Map<BundleStatus, Set<BundleStatus>> TABLE = build();

    private static Map<BundleStatus, Set<BundleStatus>> build() {
        Map<BundleStatus, Set<BundleStatus>> t = new EnumMap<>(BundleStatus.class);
        t.put(OPEN,     Set.of(COMPLETE));
        t.put(COMPLETE, Set.of(OPEN, LOCKED));
        t.put(LOCKED,   Set.of());          // terminal — irreversible by design
        return t;
    }

    public static boolean isLegal(BundleStatus from, BundleStatus to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<BundleStatus> allowedTargets(BundleStatus from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
