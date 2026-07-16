package com.notarist.search.application.strategy;

import com.notarist.search.application.port.out.RagPort;
import com.notarist.search.application.routing.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Document intelligence: "ringkas dokumen ini", "jelaskan klausul ini", and any open question that
 * did not classify as a fact.
 *
 * <p>This is the legitimate home of the LLM — and the only place it is the primary engine. Answers
 * are grounded in retrieved chunks, cited before generation, and checked afterwards by the existing
 * hallucination guard, all of which live behind {@link RagPort} in the assistant module.
 *
 * <p>Note this is the <em>fallback</em> strategy (it supports OPEN_QUESTION), which is precisely why
 * everything factual is classified ahead of it. Before this sprint, this pipeline was not the
 * fallback — it was the <em>only</em> path, so "berapa akta bulan ini" landed here too.
 */
@Component
@Order(30)
public class DocumentQaStrategy implements StreamingAnswerStrategy {

    private final RagPort ragPort;

    public DocumentQaStrategy(RagPort ragPort) {
        this.ragPort = ragPort;
    }

    @Override
    public String name() {
        return "DocumentQaStrategy";
    }

    @Override
    public boolean supports(ClassifiedQuery query) {
        return query.subtype() == QuerySubtype.SUMMARIZE
                || query.subtype() == QuerySubtype.EXPLAIN
                || query.subtype() == QuerySubtype.OPEN_QUESTION;
    }

    @Override
    public boolean usesLlm() {
        return true;
    }

    @Override
    public AnswerResult execute(ClassifiedQuery query, AnswerRequest request) {
        long start = System.currentTimeMillis();

        RagPort.SynthesisMode mode = switch (query.subtype()) {
            case SUMMARIZE -> RagPort.SynthesisMode.SUMMARIZE;
            case EXPLAIN   -> RagPort.SynthesisMode.EXPLAIN;
            default        -> RagPort.SynthesisMode.ANSWER;
        };

        RagPort.RagAnswer answer = ragPort.synthesize(RagRequests.of(request, mode));
        return RagResults.toAnswerResult(answer, name(), System.currentTimeMillis() - start);
    }

    /** Streaming variant — only LLM-backed strategies can stream; a SQL COUNT has no tokens. */
    @Override
    public AnswerResult executeStreaming(ClassifiedQuery query, AnswerRequest request, AnswerTokenSink sink) {
        long start = System.currentTimeMillis();
        RagPort.RagAnswer answer = ragPort.synthesizeStreaming(
                RagRequests.of(request, RagPort.SynthesisMode.ANSWER), sink);
        return RagResults.toAnswerResult(answer, name(), System.currentTimeMillis() - start);
    }
}
