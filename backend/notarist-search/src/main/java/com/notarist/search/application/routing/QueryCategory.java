package com.notarist.search.application.routing;

/**
 * Top-level question category. Determines whether an LLM is even eligible to answer.
 *
 * <p>This is the enforcement point for the product rule: a number, a legal status, or a deadline is
 * a <em>fact</em>, and facts come from the database. An LLM reading retrieved text chunks may only
 * ever <em>infer</em> such a value, and an inferred legal status in a notary office is a liability,
 * not a feature.
 *
 * <p>{@link #FACTUAL} and {@link #STATUS} are LLM-forbidden by construction, enforced by
 * {@link FactualQueryGuard}.
 */
public enum QueryCategory {

    /** Counts, sums, aggregations, deadlines. Answer comes from SQL. LLM strictly forbidden. */
    FACTUAL(false),

    /** Lifecycle/legal state of a specific entity ("is deed 125 finalized?"). SQL. LLM forbidden. */
    STATUS(false),

    /** "Find similar clauses / similar APHT" — hybrid retrieval; result is a ranked document list. */
    SEMANTIC(true),

    /** Summarize / explain / compare document content — full RAG synthesis over retrieved chunks. */
    DOCUMENT_INTELLIGENCE(true),

    /** Could not be classified. Falls back to the (grounded, cited) RAG path. */
    UNKNOWN(true);

    private final boolean llmEligible;

    QueryCategory(boolean llmEligible) {
        this.llmEligible = llmEligible;
    }

    /**
     * Whether an LLM may produce the answer for this category.
     *
     * <p>False does NOT merely mean "prefer SQL" — it means an LLM invocation on this path is a bug
     * and {@link FactualQueryGuard} will fail the request rather than let it through.
     */
    public boolean isLlmEligible() {
        return llmEligible;
    }
}
