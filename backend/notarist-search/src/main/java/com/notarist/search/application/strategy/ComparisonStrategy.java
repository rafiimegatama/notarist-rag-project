package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.RagPort;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Comparison: "bandingkan dua dokumen ini", "apa perbedaan dengan perjanjian sebelumnya".
 *
 * <p>RAG synthesis in COMPARE mode. Comparison is genuinely generative work — it requires reading
 * both texts and articulating a difference — so the LLM is the right engine here.
 *
 * <p>One caveat the classifier already handles: "berapa selisih nilai jaminan" contains a comparison
 * word but asks for a <em>number</em>. The classifier tests counting patterns before comparison
 * patterns, so that query routes to {@link StatisticsStrategy} and never reaches this class. A
 * numeric difference is arithmetic, not interpretation.
 */
@Component
@Order(21)
public class ComparisonStrategy implements AnswerStrategy {

    private final RagPort ragPort;

    public ComparisonStrategy(RagPort ragPort) {
        this.ragPort = ragPort;
    }

    @Override
    public String name() {
        return "ComparisonStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.COMPARISON;
    }

    @Override
    public boolean usesLlm() {
        return true;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();
        RagPort.RagAnswer answer = ragPort.synthesize(
                RagRequests.of(request, RagPort.SynthesisMode.COMPARE));
        return RagResults.toAnswerResult(answer, name(), System.currentTimeMillis() - start);
    }
}
