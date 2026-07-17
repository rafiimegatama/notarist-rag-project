package com.notarist.kase.domain.specification;

/**
 * A named, composable business rule.
 *
 * <p>Specifications exist so that a rule like "this case may be finalized" has ONE definition, usable
 * in three places that would otherwise drift apart: guarding a transition, filtering a query, and
 * telling the UI which buttons to enable. When that rule lives as an inline {@code if} in a service,
 * the other two callers quietly reimplement it slightly differently.
 *
 * <p>{@link #reasonUnsatisfied()} is not decoration — a rule that can only say "no" forces the UI to
 * guess why, and users to guess harder.
 */
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    /** Human-readable explanation of why the candidate failed. Shown to the user. */
    String reasonUnsatisfied();

    default Specification<T> and(Specification<T> other) {
        Specification<T> self = this;
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(T candidate) {
                return self.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
            }
            @Override public String reasonUnsatisfied() {
                return self.reasonUnsatisfied() + " AND " + other.reasonUnsatisfied();
            }
        };
    }

    default Specification<T> or(Specification<T> other) {
        Specification<T> self = this;
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(T candidate) {
                return self.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
            }
            @Override public String reasonUnsatisfied() {
                return self.reasonUnsatisfied() + " OR " + other.reasonUnsatisfied();
            }
        };
    }

    default Specification<T> not() {
        Specification<T> self = this;
        return new Specification<>() {
            @Override public boolean isSatisfiedBy(T candidate) {
                return !self.isSatisfiedBy(candidate);
            }
            @Override public String reasonUnsatisfied() {
                return "NOT (" + self.reasonUnsatisfied() + ")";
            }
        };
    }
}
