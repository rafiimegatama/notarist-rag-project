package com.notarist.kase.domain.specification;

import com.notarist.kase.domain.model.Bundle;
import com.notarist.kase.domain.state.BundleStatus;

public final class BundleSpecifications {

    private BundleSpecifications() {}

    public static Specification<Bundle> isComplete() {
        return of(Bundle::isComplete, "the bundle is missing documents");
    }

    public static Specification<Bundle> canBeLocked() {
        return of(b -> b.status() == BundleStatus.COMPLETE,
                "only a COMPLETE bundle may be locked");
    }

    public static Specification<Bundle> acceptsDocuments() {
        return of(b -> b.status().acceptsDocuments(),
                "the bundle is locked and its document set is frozen");
    }

    private static Specification<Bundle> of(java.util.function.Predicate<Bundle> p, String reason) {
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(Bundle candidate) { return p.test(candidate); }
            @Override public String reasonUnsatisfied() { return reason; }
        };
    }
}
