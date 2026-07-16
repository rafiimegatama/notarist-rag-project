package com.notarist.search.application.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactualQueryGuardTest {

    private final FactualQueryGuard guard = new FactualQueryGuard();

    private ClassifiedQuery query(QuerySubtype subtype) {
        return new ClassifiedQuery(subtype, "q", Map.of());
    }

    private AnswerStrategy strategy(String name, boolean usesLlm) {
        return new AnswerStrategy() {
            @Override public String name() { return name; }
            @Override public boolean supports(ClassifiedQuery q) { return true; }
            @Override public boolean usesLlm() { return usesLlm; }
            @Override public AnswerResult execute(ClassifiedQuery q, AnswerRequest r) {
                return AnswerResult.fromSql("x", Map.of(), name, 1);
            }
        };
    }

    @ParameterizedTest
    @DisplayName("pre-flight: an LLM strategy is rejected for every LLM-forbidden subtype")
    @EnumSource(value = QuerySubtype.class,
            names = {"STATISTICS", "AGGREGATION", "REMINDER", "STATUS_LOOKUP", "IDENTIFIER_LOOKUP"})
    void rejectsLlmStrategyForForbiddenCategories(QuerySubtype subtype) {
        assertThatThrownBy(() ->
                guard.assertStrategyAllowed(query(subtype), strategy("RogueLlm", true)))
                .isInstanceOf(FactualQueryGuard.LlmForbiddenException.class)
                .hasMessageContaining("must be answered from the database");
    }

    @ParameterizedTest
    @DisplayName("pre-flight: a SQL strategy is always allowed for forbidden categories")
    @EnumSource(value = QuerySubtype.class,
            names = {"STATISTICS", "AGGREGATION", "REMINDER", "STATUS_LOOKUP", "IDENTIFIER_LOOKUP"})
    void allowsSqlStrategyForForbiddenCategories(QuerySubtype subtype) {
        assertThatCode(() ->
                guard.assertStrategyAllowed(query(subtype), strategy("Sql", false)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("pre-flight: LLM strategies are allowed for semantic / document-intelligence")
    @EnumSource(value = QuerySubtype.class,
            names = {"SIMILARITY", "SUMMARIZE", "EXPLAIN", "COMPARISON", "OPEN_QUESTION"})
    void allowsLlmForEligibleCategories(QuerySubtype subtype) {
        assertThatCode(() ->
                guard.assertStrategyAllowed(query(subtype), strategy("Rag", true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("post-flight: a result that invoked an LLM for a factual query is rejected, not returned")
    void rejectsResultThatInvokedLlmForFactualQuery() {
        // Catches a strategy that lies about usesLlm() — the pre-flight check trusts the declaration,
        // this one checks what actually happened. Returning the answer would be worse than an error:
        // the user cannot tell a hallucinated count from a real one, and will act on it.
        AnswerResult llmBacked = AnswerResult.fromRag(
                "sekitar 40-an akta", List.of(), List.of(), 0.8f, "HIGH", false, "Sneaky", 5, 3);

        assertThatThrownBy(() -> guard.assertResultAllowed(query(QuerySubtype.STATISTICS), llmBacked))
                .isInstanceOf(FactualQueryGuard.LlmForbiddenException.class)
                .hasMessageContaining("invoked an LLM");
    }

    @Test
    @DisplayName("post-flight: a SQL-backed result passes")
    void allowsSqlBackedResult() {
        AnswerResult sqlBacked = AnswerResult.fromSql("42", Map.of("count", 42L), "Statistics", 3);

        assertThatCode(() -> guard.assertResultAllowed(query(QuerySubtype.STATISTICS), sqlBacked))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unsupported factual answer never falls back to the LLM")
    void neverFallsBackToLlm() {
        // The most tempting future change to this codebase is "SQL had no answer, so just ask the
        // model". That would silently reintroduce fabricated legal answers. It must stay impossible.
        for (QuerySubtype subtype : QuerySubtype.values()) {
            assertThat(guard.mayFallBackToLlm(query(subtype)))
                    .as("fallback to LLM must never be permitted, including for %s", subtype)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("an unsupported result is honest, not empty — and is never LLM-backed")
    void unsupportedResultIsHonest() {
        AnswerResult result = AnswerResult.unsupported(
                "Data belum tersedia", "ReminderStrategy", 2);

        assertThat(result.supported()).isFalse();
        assertThat(result.metadata().llmInvoked()).isFalse();
        assertThat(result.answerText()).contains("belum tersedia");
        assertThat(result.warnings()).isNotEmpty();
    }
}
