package com.notarist.assistant.application.service;

import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.pipeline.*;
import com.notarist.assistant.application.port.out.LlmPort;
import com.notarist.assistant.application.port.out.SearchPort;
import com.notarist.assistant.domain.model.*;
import com.notarist.search.application.routing.AnswerTokenSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The RAG pipeline, extracted verbatim from {@code AssistantOrchestrator}.
 *
 * <p>This is now the <em>only</em> place in the codebase that knows how to invoke a language model.
 * It is reachable exclusively through {@code RagPort}, which only LLM-eligible strategies hold. The
 * assistant's own orchestrator no longer references {@link LlmPort} at all — it asks the router for
 * an answer and does not know, or need to know, which engine produced it.
 *
 * <p>The pipeline's ordering guarantees are unchanged and remain mandatory:
 * retrieve → budget → <b>cite before generating</b> → evaluate grounding → short-circuit if
 * insufficient under STRICT → prompt → LLM → detect unsupported claims → hallucination guard.
 */
@Service
public class RagAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);

    private final SearchPort                    searchPort;
    private final AssistantContextBudgetManager contextBudgetManager;
    private final CitationInjector              citationInjector;
    private final GroundingEvaluator            groundingEvaluator;
    private final RetrievalContextAssembler     contextAssembler;
    private final PromptBuilder                 promptBuilder;
    private final LlmPort                       llmPort;
    private final UnsupportedClaimDetector      claimDetector;
    private final HallucinationGuard            hallucinationGuard;

    public RagAnswerService(
            SearchPort searchPort,
            AssistantContextBudgetManager contextBudgetManager,
            CitationInjector citationInjector,
            GroundingEvaluator groundingEvaluator,
            RetrievalContextAssembler contextAssembler,
            PromptBuilder promptBuilder,
            LlmPort llmPort,
            UnsupportedClaimDetector claimDetector,
            HallucinationGuard hallucinationGuard) {
        this.searchPort           = searchPort;
        this.contextBudgetManager = contextBudgetManager;
        this.citationInjector     = citationInjector;
        this.groundingEvaluator   = groundingEvaluator;
        this.contextAssembler     = contextAssembler;
        this.promptBuilder        = promptBuilder;
        this.llmPort              = llmPort;
        this.claimDetector        = claimDetector;
        this.hallucinationGuard   = hallucinationGuard;
    }

    /** @param sink null → blocking invocation; non-null → token-level streaming. */
    public RagOutcome answer(RagInput input, AnswerTokenSink sink) {
        UUID retrievalSnapshotId = input.retrievalSnapshotId();

        // 1. Retrieval
        SearchPort.SearchResult searchResult = searchPort.search(new SearchPort.AssistantSearchRequest(
                input.rawQuery(),
                input.tenantId(),
                input.userId(),
                input.maxClassificationLevel(),
                input.documentTypeFilter(),
                input.maxResults(),
                input.contextTokenBudget(),
                retrievalSnapshotId));

        // 2. Context budget: dedup + prioritize + truncate
        AssistantContextBudgetManager.BudgetResult budget =
                contextBudgetManager.applyBudget(searchResult.chunks(), input.contextTokenBudget());

        // 3. Citations BEFORE the LLM — mandatory
        List<CitationDto> citations = citationInjector.buildCitations(budget.selectedChunks());

        // 4. Grounding evaluation BEFORE the LLM
        AssistantSafetyMode safetyMode = input.strictMode()
                ? AssistantSafetyMode.STRICT : AssistantSafetyMode.BALANCED;
        AnswerConfidence preLlmConfidence = groundingEvaluator.evaluate(
                searchResult.groundingScore(), budget.selectedChunks().size(), safetyMode);

        // 5. Short-circuit: no grounding in STRICT mode → never call the model
        if (preLlmConfidence == AnswerConfidence.INSUFFICIENT && safetyMode == AssistantSafetyMode.STRICT) {
            log.warn("RAG short-circuit: INSUFFICIENT grounding in STRICT mode — no LLM call");
            String fallback = hallucinationGuard.getFallbackMessage();
            if (sink != null) sink.onToken(fallback);
            return new RagOutcome(
                    fallback, citations,
                    List.of("Grounding tidak cukup untuk memberikan jawaban yang dapat dipercaya."),
                    0f, AnswerConfidence.INSUFFICIENT, true, true,
                    searchResult.chunks().size());
        }

        // 6-7. Assemble context and build the versioned prompt
        String assembledContext = contextAssembler.assemble(budget.selectedChunks(), citations);
        List<String> chunkIds = budget.selectedChunks().stream()
                .map(SearchPort.RetrievedChunkDto::chunkId)
                .collect(Collectors.toList());
        AssembledPrompt prompt = promptBuilder.build(
                applyMode(input),
                assembledContext,
                PromptVersion.V1_LEGAL_ID,
                retrievalSnapshotId,
                chunkIds);

        // 8. LLM invocation
        LlmRequest llmRequest = LlmRequest.strict(
                prompt.systemPrompt(), prompt.userPrompt(), input.traceId());
        String answerContent = (sink != null)
                ? invokeStreaming(llmRequest, sink)
                : llmPort.invoke(llmRequest).content();

        // 9-10. Post-LLM: unsupported claims, then the hallucination guard
        List<String> unsupportedClaims = claimDetector.detect(answerContent, citations);
        GuardResult guardResult = hallucinationGuard.guard(
                answerContent, preLlmConfidence, unsupportedClaims, safetyMode);

        String finalAnswer = guardResult.downgraded()
                ? hallucinationGuard.getFallbackMessage()
                : answerContent;

        return new RagOutcome(
                finalAnswer,
                citations,
                guardResult.warnings(),
                searchResult.groundingScore(),
                guardResult.adjustedConfidence(),
                guardResult.hasWarnings(),
                guardResult.downgraded(),
                searchResult.chunks().size());
    }

    public boolean cancel(String traceId)      { return llmPort.cancelStream(traceId); }
    public void openStream(String traceId)     { llmPort.openStream(traceId); }
    public void closeStream(String traceId)    { llmPort.closeStream(traceId); }

    /**
     * Shapes the user prompt for the requested synthesis mode. The instruction is prepended to the
     * question; the retrieval context and citation rules still come from the versioned system prompt,
     * so grounding behaviour is identical across modes.
     */
    private String applyMode(RagInput input) {
        return switch (input.mode()) {
            case SUMMARIZE -> "Ringkas dokumen berikut secara padat dan faktual. " + input.rawQuery();
            case EXPLAIN   -> "Jelaskan dengan bahasa yang jelas dan tepat. " + input.rawQuery();
            case COMPARE   -> "Bandingkan dokumen/klausul berikut dan uraikan perbedaannya. " + input.rawQuery();
            case ANSWER    -> input.rawQuery();
        };
    }

    private String invokeStreaming(LlmRequest llmRequest, AnswerTokenSink sink) {
        StringBuilder accumulated = new StringBuilder();
        llmPort.stream(llmRequest, chunk -> {
            if (chunk.done()) return;
            String delta = chunk.delta();
            if (delta == null || delta.isEmpty()) return;
            accumulated.append(delta);
            sink.onToken(delta);
        });
        return accumulated.toString();
    }

    /** Input to the pipeline, free of any router/port types. */
    public record RagInput(
            String rawQuery,
            UUID tenantId,
            UUID userId,
            String maxClassificationLevel,
            String documentTypeFilter,
            int maxResults,
            int contextTokenBudget,
            boolean strictMode,
            String traceId,
            UUID retrievalSnapshotId,
            Mode mode
    ) {
        public enum Mode { ANSWER, SUMMARIZE, EXPLAIN, COMPARE }
    }

    /** Everything the caller needs to build a response, with no LLM types leaking out. */
    public record RagOutcome(
            String answerText,
            List<CitationDto> citations,
            List<String> warnings,
            float groundingScore,
            AnswerConfidence confidence,
            boolean hallucinationWarning,
            boolean downgraded,
            int documentsRetrieved
    ) {}
}
