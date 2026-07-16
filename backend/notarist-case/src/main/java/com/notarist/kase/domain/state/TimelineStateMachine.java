package com.notarist.kase.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.kase.domain.state.TimelineStatus.*;

/** ACTIVE → SEALED. One edge, one direction, no way back. */
public final class TimelineStateMachine {

    private TimelineStateMachine() {}

    private static final Map<TimelineStatus, Set<TimelineStatus>> TABLE = build();

    private static Map<TimelineStatus, Set<TimelineStatus>> build() {
        Map<TimelineStatus, Set<TimelineStatus>> t = new EnumMap<>(TimelineStatus.class);
        t.put(ACTIVE, Set.of(SEALED));
        t.put(SEALED, Set.of());
        return t;
    }

    public static boolean isLegal(TimelineStatus from, TimelineStatus to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<TimelineStatus> allowedTargets(TimelineStatus from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
