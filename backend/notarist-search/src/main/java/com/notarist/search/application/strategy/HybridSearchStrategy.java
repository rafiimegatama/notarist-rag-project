package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.RagPort;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Similarity questions that ask the system to reason, not just retrieve:
 * "klausul indemnity yang mirip dengan ini", "bandingkan dengan APHT sebelumnya".
 *
 * <p>Section C of the specification: BM25 + vector + reranker + <b>LLM synthesis</b>. Retrieval and
 * ranking happen inside the RAG pipeline behind {@link RagPort}; the model then composes an answer
 * grounded in — and cited to — the retrieved chunks.
 *
 * <p>This strategy is LLM-eligible, which means {@link FactualQueryGuard} will refuse to let it run
 * if the classifier ever hands it a factual or status question. The model may explain what a clause
 * says; it may never tell you how many of them exist, or whether one was signed.
 */
@Component
@Order(20)
public class HybridSearchStrategy implements AnswerStrategy {

    private final RagPort ragPort;

    public HybridSearchStrategy(RagPort ragPort) {
        this.ragPort = ragPort;
    }

    @Override
    public String name() {
        return "HybridSearchStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.SIMILARITY;
    }

    @Override
    public boolean usesLlm() {
        return true;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        RagPort.RagAnswer answer = ragPort.synthesize(
                RagRequests.of(request, RagPort.SynthesisMode.ANSWER));

        return RagResults.toAnswerResult(answer, name(), System.currentTimeMillis() - start);
    }
}
