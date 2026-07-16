package com.notarist.review.domain.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.notarist.review.domain.state.ReviewStatus.*;

/**
 * The authoritative review-status transition table. Nothing that is not listed here is possible.
 *
 * <p>Used only from inside the {@code OcrReview} aggregate — it is package-visible behaviour, not a
 * helper a service may call to "check first and then mutate". The aggregate has no public status
 * setter, so this table is the single gate through which status changes pass.
 */
public final class ReviewStatusMachine {

    private ReviewStatusMachine() {}

    private static final Map<ReviewStatus, Set<ReviewStatus>> TABLE = build();

    private static Map<ReviewStatus, Set<ReviewStatus>> build() {
        Map<ReviewStatus, Set<ReviewStatus>> t = new EnumMap<>(ReviewStatus.class);
        t.put(PENDING, Set.of(IN_PROGRESS));
        t.put(IN_PROGRESS, Set.of(REVIEW_COMPLETED));
        t.put(REVIEW_COMPLETED, Set.of(VERIFIED));
        t.put(VERIFIED, Set.of());   // terminal — no outbound edges, deliberately empty
        return t;
    }

    public static boolean isLegal(ReviewStatus from, ReviewStatus to) {
        return TABLE.getOrDefault(from, Set.of()).contains(to);
    }

    /** Every status reachable from {@code from}. Lets the UI offer only lawful actions. */
    public static Set<ReviewStatus> allowedTargets(ReviewStatus from) {
        return TABLE.getOrDefault(from, Set.of());
    }
}
