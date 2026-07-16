package com.notarist.assistant.infrastructure.adapter;

import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.service.RagAnswerService;
import com.notarist.search.application.port.out.RagPort;
import com.notarist.search.application.routing.AnswerCitation;
import com.notarist.search.application.routing.AnswerTokenSink;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the search module's {@link RagPort} on top of the assistant's RAG pipeline.
 *
 * <p>This adapter is the hinge of the whole design. The port is <em>declared</em> in
 * {@code notarist-search} (the consumer) and <em>implemented</em> here (the provider), so:
 *
 * <ul>
 *   <li>the router can call the LLM path without {@code notarist-search} depending on
 *       {@code notarist-assistant} — no dependency cycle; and</li>
 *   <li>every LLM concern stays behind this wall, where only LLM-eligible strategies can reach it.</li>
 * </ul>
 *
 * The factual strategies do not hold this port, so they cannot invoke a model even by mistake.
 */
@Component
public class RagAnswerAdapter implements RagPort {

    private final RagAnswerService ragAnswerService;

    public RagAnswerAdapter(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    @Override
    public RagAnswer synthesize(RagRequest request) {
        return toRagAnswer(ragAnswerService.answer(toInput(request), null));
    }

    @Override
    public RagAnswer synthesizeStreaming(RagRequest request, AnswerTokenSink sink) {
        return toRagAnswer(ragAnswerService.answer(toInput(request), sink));
    }

    @Override
    public boolean cancel(String traceId) {
        return ragAnswerService.cancel(traceId);
    }

    @Override
    public void openStream(String traceId) {
        ragAnswerService.openStream(traceId);
    }

    @Override
    public void closeStream(String traceId) {
        ragAnswerService.closeStream(traceId);
    }

    private RagAnswerService.RagInput toInput(RagRequest r) {
        return new RagAnswerService.RagInput(
                r.rawQuery(),
                r.tenantId(),
                r.userId(),
                r.maxClassificationLevel(),
                r.documentTypeFilter(),
                r.maxResults(),
                r.contextTokenBudget(),
                r.strictMode(),
                r.traceId() != null ? r.traceId() : UUID.randomUUID().toString(),
                r.correlationId() != null ? r.correlationId() : UUID.randomUUID(),
                switch (r.mode()) {
                    case SUMMARIZE -> RagAnswerService.RagInput.Mode.SUMMARIZE;
                    case EXPLAIN   -> RagAnswerService.RagInput.Mode.EXPLAIN;
                    case COMPARE   -> RagAnswerService.RagInput.Mode.COMPARE;
                    case ANSWER    -> RagAnswerService.RagInput.Mode.ANSWER;
                });
    }

    private RagAnswer toRagAnswer(RagAnswerService.RagOutcome outcome) {
        List<AnswerCitation> citations = outcome.citations().stream()
                .map(this::toAnswerCitation)
                .collect(Collectors.toList());

        return new RagAnswer(
                outcome.answerText(),
                citations,
                outcome.warnings(),
                outcome.groundingScore(),
                outcome.confidence().name(),
                outcome.downgraded(),
                outcome.documentsRetrieved());
    }

    private AnswerCitation toAnswerCitation(CitationDto c) {
        return new AnswerCitation(
                c.chunkId(),
                c.documentId(),
                c.documentType(),
                c.classificationLevel(),
                c.sectionTitle(),
                c.chunkIndex(),
                c.chunkText(),
                c.sourceObjectKey(),
                c.relevanceScore());
    }
}
