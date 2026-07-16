package com.notarist.search.application.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chooses an execution strategy for a question. It does not choose a <em>provider</em>, and it does
 * not execute anything itself — strategies own their dependencies.
 *
 * <pre>
 *   question → classify → select strategy → guard → execute → guard → result
 * </pre>
 *
 * <p>This is the component that stops the LLM being the default execution engine. Before it, there
 * was one pipeline (BM25 + vector + rerank + LLM) and everything went through it, including "how
 * many deeds this month" — a question with an exact answer that a language model can only estimate.
 *
 * <p>Strategies are injected as an ordered list (Spring honours {@code @Order}). The first one whose
 * {@link AnswerStrategy#supports} returns true wins. Order therefore encodes precedence, and the
 * deterministic strategies are ordered ahead of the generative ones so that a tie is always resolved
 * in favour of the engine that cannot make something up.
 */
@Service
public class AnswerRouter {

    private static final Logger log = LoggerFactory.getLogger(AnswerRouter.class);

    private final QueryClassifier classifier;
    private final FactualQueryGuard guard;
    private final List<AnswerStrategy> strategies;
    private final com.notarist.search.application.port.out.RagPort ragPort;

    public AnswerRouter(QueryClassifier classifier,
                        FactualQueryGuard guard,
                        List<AnswerStrategy> strategies,
                        com.notarist.search.application.port.out.RagPort ragPort) {
        this.classifier = classifier;
        this.guard = guard;
        this.strategies = strategies;
        this.ragPort = ragPort;
    }

    /**
     * Routes and answers. Streaming is handled by the strategy itself (only LLM-eligible strategies
     * can stream — there are no tokens to stream from a SQL COUNT).
     */
    public AnswerResult route(AnswerRequest request) {
        ClassifiedQuery classified = classifier.classify(request.rawQuery());

        MDC.put("queryCategory", classified.category().name());
        MDC.put("querySubtype", classified.subtype().name());

        try {
            AnswerStrategy strategy = select(classified);

            // Pre-flight: an LLM strategy must never be selected for a factual/status question.
            guard.assertStrategyAllowed(classified, strategy);

            log.info("Routing category={} subtype={} → strategy={} llmEligible={}",
                    classified.category(), classified.subtype(), strategy.name(), classified.isLlmEligible());

            AnswerResult result = strategy.execute(classified, request);

            // Post-flight: catches a strategy that misreports usesLlm(). Fails the request rather
            // than returning a model-generated answer to a factual question.
            guard.assertResultAllowed(classified, result);

            log.info("Answered strategy={} llmInvoked={} sqlInvoked={} docs={} citations={} ms={}",
                    result.metadata().strategyUsed(),
                    result.metadata().llmInvoked(),
                    result.metadata().sqlInvoked(),
                    result.metadata().documentsRetrieved(),
                    result.metadata().citationsCount(),
                    result.metadata().executionTimeMs());

            return result;

        } finally {
            MDC.remove("queryCategory");
            MDC.remove("querySubtype");
        }
    }

    /**
     * Routes and answers, streaming tokens to {@code sink}.
     *
     * <p>An LLM-backed strategy streams real tokens. A deterministic strategy has no tokens to
     * stream — a COUNT is a number, not a sequence — so its finished answer is emitted as a single
     * token. The transport is therefore identical either way, which is what lets the existing SSE
     * endpoint keep working unchanged regardless of which engine answered.
     */
    public AnswerResult routeStreaming(AnswerRequest request, AnswerTokenSink sink) {
        ClassifiedQuery classified = classifier.classify(request.rawQuery());
        AnswerStrategy strategy = select(classified);

        guard.assertStrategyAllowed(classified, strategy);

        log.info("Routing (stream) category={} subtype={} → strategy={} streaming={}",
                classified.category(), classified.subtype(), strategy.name(),
                strategy instanceof StreamingAnswerStrategy);

        AnswerResult result;
        if (strategy instanceof StreamingAnswerStrategy streaming) {
            result = streaming.executeStreaming(classified, request, sink);
        } else {
            result = strategy.execute(classified, request);
            // Deterministic answer: emit whole, so the client's token stream still completes.
            if (result.answerText() != null) sink.onToken(result.answerText());
        }

        guard.assertResultAllowed(classified, result);
        return result;
    }

    /** Opens a cancellable inference scope. No-op for deterministic strategies. */
    public void openStream(String traceId) {
        ragControl().ifPresent(rag -> rag.openStream(traceId));
    }

    /** Closes the inference scope. */
    public void closeStream(String traceId) {
        ragControl().ifPresent(rag -> rag.closeStream(traceId));
    }

    /** Cancels an in-flight inference. Idempotent; false when nothing was cancellable. */
    public boolean cancelStream(String traceId) {
        return ragControl().map(rag -> rag.cancel(traceId)).orElse(false);
    }

    /**
     * Stream lifecycle is an LLM concern, so it is delegated to the RAG port. The router holds this
     * only to control inference scope — it never uses it to <em>answer</em>. Answering is always the
     * selected strategy's job, and the guard polices that independently.
     */
    private java.util.Optional<com.notarist.search.application.port.out.RagPort> ragControl() {
        return java.util.Optional.ofNullable(ragPort);
    }

    /** Exposed so callers can inspect the classification without executing anything. */
    public ClassifiedQuery classify(String rawQuery) {
        return classifier.classify(rawQuery);
    }

    /** True when the routed strategy is permitted to stream tokens (i.e. it is LLM-backed). */
    public boolean isStreamable(ClassifiedQuery classified) {
        return classified.isLlmEligible() && select(classified).usesLlm();
    }

    private AnswerStrategy select(ClassifiedQuery classified) {
        return strategies.stream()
                .filter(s -> s.supports(classified))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No strategy supports subtype " + classified.subtype()
                                + " — the strategy set must be exhaustive over QuerySubtype."));
    }
}
