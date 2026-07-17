package com.notarist.search.application.routing;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.search.application.port.out.RagPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Routing behaviour. The single most important assertion in this file — and arguably in the sprint —
 * is {@link #factualQueriesNeverReachTheLlm()}: it fails if any factual or status question causes an
 * LLM invocation, which is the defect this sprint exists to fix.
 */
class AnswerRouterTest {

    private final QueryClassifier classifier = new QueryClassifier();
    private final FactualQueryGuard guard = new FactualQueryGuard();

    /** Records whether it was called, so a test can prove the LLM was NOT reached. */
    private static class SpyLlmStrategy implements AnswerStrategy {
        final AtomicBoolean invoked = new AtomicBoolean(false);
        private final QuerySubtype[] supported;

        SpyLlmStrategy(QuerySubtype... supported) {
            this.supported = supported;
        }

        @Override public String name() { return "SpyLlmStrategy"; }
        @Override public boolean usesLlm() { return true; }

        @Override
        public boolean supports(ClassifiedQuery query) {
            for (QuerySubtype s : supported) if (s == query.subtype()) return true;
            return false;
        }

        @Override
        public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
            invoked.set(true);
            return AnswerResult.fromRag("llm answer", List.of(), List.of(), 0.9f, "HIGH", false, name(), 1, 3);
        }
    }

    /** Deterministic stand-in for the SQL strategies. */
    private static class FakeSqlStrategy implements AnswerStrategy {
        final AtomicBoolean invoked = new AtomicBoolean(false);
        private final String name;
        private final QuerySubtype[] supported;

        FakeSqlStrategy(String name, QuerySubtype... supported) {
            this.name = name;
            this.supported = supported;
        }

        @Override public String name() { return name; }
        @Override public boolean usesLlm() { return false; }

        @Override
        public boolean supports(ClassifiedQuery query) {
            for (QuerySubtype s : supported) if (s == query.subtype()) return true;
            return false;
        }

        @Override
        public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
            invoked.set(true);
            return AnswerResult.fromSql("42", Map.of("count", 42L), name, 1);
        }
    }

    private FakeSqlStrategy statistics;
    private FakeSqlStrategy aggregation;
    private FakeSqlStrategy reminder;
    private FakeSqlStrategy structured;
    private SpyLlmStrategy llm;
    private AnswerRouter router;

    @BeforeEach
    void setUp() {
        statistics = new FakeSqlStrategy("StatisticsStrategy", QuerySubtype.STATISTICS);
        aggregation = new FakeSqlStrategy("AggregationStrategy", QuerySubtype.AGGREGATION);
        reminder = new FakeSqlStrategy("ReminderStrategy", QuerySubtype.REMINDER);
        structured = new FakeSqlStrategy("StructuredSearchStrategy",
                QuerySubtype.STATUS_LOOKUP, QuerySubtype.IDENTIFIER_LOOKUP);
        llm = new SpyLlmStrategy(QuerySubtype.SIMILARITY, QuerySubtype.SUMMARIZE,
                QuerySubtype.EXPLAIN, QuerySubtype.COMPARISON, QuerySubtype.OPEN_QUESTION);

        router = new AnswerRouter(
                classifier, guard,
                List.of(statistics, aggregation, reminder, structured, llm),
                mock(RagPort.class));
    }

    private AnswerRequest request(String query) {
        return new AnswerRequest(
                query, UUID.randomUUID(), UUID.randomUUID(),
                ClassificationLevel.INTERNAL, null, 10, 3072, true,
                UUID.randomUUID().toString(), CorrelationId.generate());
    }

    // ---- The core safety property -----------------------------------------------------------

    @ParameterizedTest
    @DisplayName("NO factual or status query ever invokes the LLM")
    @ValueSource(strings = {
            "berapa akta bulan ini",
            "berapa bundle hari ini",
            "berapa dokumen yang gagal OCR",
            "SKMHT yang jatuh tempo minggu depan",
            "apakah akta 125 sudah final",
            "apakah bundle ABC sudah dikirim",
            "siapa yang menyetujui bundle ini",
            "berapa akta per jenis",
            "akta nomor 125/VII/2024"
    })
    void factualQueriesNeverReachTheLlm(String query) {
        AnswerResult result = router.route(request(query));

        assertThat(llm.invoked)
                .as("LLM was invoked for '%s' — this is the exact defect the router exists to prevent", query)
                .isFalse();
        assertThat(result.metadata().llmInvoked()).isFalse();
        assertThat(result.metadata().sqlInvoked()).isTrue();
    }

    // ---- Strategy selection -----------------------------------------------------------------

    @Test
    @DisplayName("'berapa akta bulan ini' → StatisticsStrategy")
    void statisticsRouting() {
        AnswerResult result = router.route(request("berapa akta bulan ini"));
        assertThat(result.metadata().strategyUsed()).isEqualTo("StatisticsStrategy");
        assertThat(statistics.invoked).isTrue();
    }

    @Test
    @DisplayName("'status bundle' → StructuredSearchStrategy")
    void statusRouting() {
        AnswerResult result = router.route(request("status bundle ABC"));
        assertThat(result.metadata().strategyUsed()).isEqualTo("StructuredSearchStrategy");
    }

    @Test
    @DisplayName("'berapa akta per jenis' → AggregationStrategy")
    void aggregationRouting() {
        AnswerResult result = router.route(request("berapa akta per jenis"));
        assertThat(result.metadata().strategyUsed()).isEqualTo("AggregationStrategy");
    }

    @Test
    @DisplayName("'jatuh tempo minggu depan' → ReminderStrategy")
    void reminderRouting() {
        AnswerResult result = router.route(request("SKMHT jatuh tempo minggu depan"));
        assertThat(result.metadata().strategyUsed()).isEqualTo("ReminderStrategy");
    }

    @Test
    @DisplayName("'ringkas isi dokumen' → the LLM strategy (this is its legitimate home)")
    void documentQaRouting() {
        AnswerResult result = router.route(request("ringkas isi dokumen ini"));

        assertThat(llm.invoked).isTrue();
        assertThat(result.metadata().llmInvoked()).isTrue();
        assertThat(result.metadata().sqlInvoked()).isFalse();
    }

    @Test
    @DisplayName("'dokumen mirip' → the LLM/hybrid strategy")
    void similarityRouting() {
        AnswerResult result = router.route(request("cari dokumen mirip dengan ini"));
        assertThat(result.metadata().llmInvoked()).isTrue();
    }

    // ---- Guard integration ------------------------------------------------------------------

    @Test
    @DisplayName("a misconfigured LLM strategy claiming a factual query is REJECTED, not executed")
    void guardRejectsLlmStrategyForFactualQuery() {
        // Simulates the regression this guard exists to catch: someone wires an LLM strategy so that
        // it supports STATISTICS. Without the guard, counts would silently start being hallucinated.
        SpyLlmStrategy rogue = new SpyLlmStrategy(QuerySubtype.STATISTICS);
        AnswerRouter rogueRouter = new AnswerRouter(
                classifier, guard, List.of(rogue), mock(RagPort.class));

        assertThatThrownBy(() -> rogueRouter.route(request("berapa akta bulan ini")))
                .isInstanceOf(FactualQueryGuard.LlmForbiddenException.class)
                .hasMessageContaining("FACTUAL");

        assertThat(rogue.invoked)
                .as("guard must reject BEFORE the model is called, not after")
                .isFalse();
    }

    @Test
    @DisplayName("an exhaustive strategy set is required — an unroutable query fails loudly")
    void missingStrategyFailsLoudly() {
        AnswerRouter empty = new AnswerRouter(classifier, guard, List.of(), mock(RagPort.class));

        assertThatThrownBy(() -> empty.route(request("berapa akta bulan ini")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No strategy supports");
    }

    // ---- Observability (Task 7) -------------------------------------------------------------

    @Test
    @DisplayName("every answer carries audit metadata: strategy, timing, llmInvoked, sqlInvoked")
    void answersCarryAuditMetadata() {
        AnswerResult result = router.route(request("berapa akta bulan ini"));

        AnswerResult.AnswerMetadata meta = result.metadata();
        assertThat(meta.strategyUsed()).isNotBlank();
        assertThat(meta.executionTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(meta.llmInvoked()).isFalse();
        assertThat(meta.sqlInvoked()).isTrue();
        assertThat(meta.documentsRetrieved()).isZero();
        assertThat(meta.citationsCount()).isZero();
    }

    // ---- Streaming --------------------------------------------------------------------------

    @Test
    @DisplayName("a factual answer still streams — as one token — so the SSE contract is unchanged")
    void factualAnswersStreamAsSingleToken() {
        StringBuilder streamed = new StringBuilder();

        AnswerResult result = router.routeStreaming(
                request("berapa akta bulan ini"), streamed::append);

        assertThat(streamed.toString()).isEqualTo("42");
        assertThat(result.metadata().llmInvoked()).isFalse();
    }
}
