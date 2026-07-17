package com.notarist.search.application.routing;

/**
 * One execution engine for one class of question. Strategies own their own dependencies: a SQL
 * strategy holds a repository, a RAG strategy holds a synthesis port. The router holds neither — it
 * only chooses.
 *
 * <p>This is what keeps the LLM from becoming the default execution engine. Previously there was a
 * single pipeline and every question, factual or not, flowed into it. Now a question is matched to
 * an engine, and the engines that answer facts have no path to a model at all.
 */
public interface AnswerStrategy {

    /** Stable name; surfaced as {@code strategyUsed} in audit metadata. */
    String name();

    /** Whether this strategy handles the classified query. */
    boolean supports(ClassifiedQuery query);

    /**
     * Whether this strategy may invoke an LLM. Declared statically so {@link FactualQueryGuard} can
     * reject an illegal pairing (LLM strategy selected for a factual question) <em>before</em> the
     * strategy executes, rather than detecting the violation after a model has already been called.
     */
    boolean usesLlm();

    AnswerResult execute(ClassifiedQuery query, AnswerRequest request);
}
