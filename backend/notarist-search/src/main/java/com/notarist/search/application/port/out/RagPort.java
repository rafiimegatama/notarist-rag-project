package com.notarist.search.application.port.out;

import com.notarist.search.application.routing.AnswerCitation;
import com.notarist.search.application.routing.AnswerTokenSink;

import java.util.List;

/**
 * LLM synthesis over retrieved, cited context.
 *
 * <p><b>Dependency inversion, on purpose.</b> This port is <em>declared</em> here (the consumer) and
 * <em>implemented</em> in the assistant module (the provider). That is what lets the assistant depend
 * on the router without the router depending on the assistant — no cycle — while keeping every LLM
 * concern (prompting, grounding, hallucination guard, streaming) on the assistant's side of the wall.
 *
 * <p>Only LLM-eligible strategies hold a reference to this port. The factual and status strategies
 * are not merely discouraged from calling a model — they have no way to reach one.
 */
public interface RagPort {

    /** Full RAG: retrieve → cite → ground → prompt → LLM → guard. */
    RagAnswer synthesize(RagRequest request);

    /** Same pipeline, streaming tokens to the sink as the model emits them. */
    RagAnswer synthesizeStreaming(RagRequest request, AnswerTokenSink sink);

    /** Cancels an in-flight inference by traceId. Idempotent. */
    boolean cancel(String traceId);

    /** Opens a cancellable inference scope before retrieval begins. */
    void openStream(String traceId);

    /** Closes the inference scope. */
    void closeStream(String traceId);

    record RagRequest(
            String rawQuery,
            java.util.UUID tenantId,
            java.util.UUID userId,
            String maxClassificationLevel,
            String documentTypeFilter,
            int maxResults,
            int contextTokenBudget,
            boolean strictMode,
            String traceId,
            java.util.UUID correlationId,
            /** Instruction shaping the synthesis (summarize / explain / compare). */
            SynthesisMode mode
    ) {}

    enum SynthesisMode { ANSWER, SUMMARIZE, EXPLAIN, COMPARE }

    record RagAnswer(
            String answerText,
            List<AnswerCitation> citations,
            List<String> warnings,
            float groundingScore,
            String confidence,
            boolean downgraded,
            int documentsRetrieved
    ) {}
}
