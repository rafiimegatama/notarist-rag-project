package com.notarist.assistant.application.orchestrator;

import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.command.AssistantCommand;
import com.notarist.assistant.application.pipeline.*;
import com.notarist.assistant.application.port.in.AssistantUseCase;
import com.notarist.assistant.application.port.out.AssistantAuditPort;
import com.notarist.assistant.application.port.out.LlmPort;
import com.notarist.assistant.application.port.out.SearchPort;
import com.notarist.assistant.domain.model.*;
import com.notarist.assistant.infrastructure.metrics.AssistantMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the full assistant pipeline.
 * NOT a mega service — each responsibility is delegated to its dedicated component.
 *
 * Flow (mandatory, no shortcuts):
 *   Command → Search → Budget → Citations (BEFORE LLM) → Grounding Eval
 *   → [short-circuit if INSUFFICIENT+STRICT] → Context Assembly → Prompt Build
 *   → LLM Invoke → Unsupported Claim Detection → Hallucination Guard
 *   → Follow-ups → Memory Store → Audit → Metrics → Response
 */
@Service
public class AssistantOrchestrator implements AssistantUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);

    private final SearchPort                   searchPort;
    private final AssistantContextBudgetManager contextBudgetManager;
    private final CitationInjector             citationInjector;
    private final GroundingEvaluator           groundingEvaluator;
    private final RetrievalContextAssembler    contextAssembler;
    private final PromptBuilder                promptBuilder;
    private final LlmPort                      llmPort;
    private final UnsupportedClaimDetector     claimDetector;
    private final HallucinationGuard           hallucinationGuard;
    private final FollowUpSuggestionService    followUpService;
    private final ConversationMemoryService    memoryService;
    private final AssistantAuditPort           auditPort;
    private final AssistantMetricsRegistry     metrics;

    public AssistantOrchestrator(
            SearchPort searchPort,
            AssistantContextBudgetManager contextBudgetManager,
            CitationInjector citationInjector,
            GroundingEvaluator groundingEvaluator,
            RetrievalContextAssembler contextAssembler,
            PromptBuilder promptBuilder,
            LlmPort llmPort,
            UnsupportedClaimDetector claimDetector,
            HallucinationGuard hallucinationGuard,
            FollowUpSuggestionService followUpService,
            ConversationMemoryService memoryService,
            AssistantAuditPort auditPort,
            AssistantMetricsRegistry metrics) {
        this.searchPort          = searchPort;
        this.contextBudgetManager = contextBudgetManager;
        this.citationInjector    = citationInjector;
        this.groundingEvaluator  = groundingEvaluator;
        this.contextAssembler    = contextAssembler;
        this.promptBuilder       = promptBuilder;
        this.llmPort             = llmPort;
        this.claimDetector       = claimDetector;
        this.hallucinationGuard  = hallucinationGuard;
        this.followUpService     = followUpService;
        this.memoryService       = memoryService;
        this.auditPort           = auditPort;
        this.metrics             = metrics;
    }

    @Override
    public AssistantResponse ask(AssistantCommand command) {
        long startMs = System.currentTimeMillis();
        UUID retrievalSnapshotId = UUID.randomUUID();
        ResponseTrace trace = ResponseTrace.create(
                command.queryId(), command.sessionId(),
                PromptVersion.V1_LEGAL_ID.version(), retrievalSnapshotId);

        MDC.put("traceId",  trace.traceId().toString());
        MDC.put("tenantId", command.tenantId().toString());

        try {
            metrics.recordInteractionStarted(command.safetyMode());
            log.info("Assistant ask traceId={} tenantId={} safetyMode={} query='{}'",
                    trace.traceId(), command.tenantId(), command.safetyMode(), command.rawQuery());

            // 1. Retrieval
            SearchPort.SearchResult searchResult = searchPort.search(toSearchRequest(command, retrievalSnapshotId));
            log.debug("Retrieval: {} chunks, confidence={}, score={}",
                    searchResult.chunks().size(), searchResult.groundingConfidence(), searchResult.groundingScore());

            // 2. Context budget management: dedup + prioritize + truncate
            AssistantContextBudgetManager.BudgetResult budget =
                    contextBudgetManager.applyBudget(searchResult.chunks(), command.contextTokenBudget());

            // 3. Citations BEFORE LLM — mandatory
            List<CitationDto> citations = citationInjector.buildCitations(budget.selectedChunks());

            // 4. Grounding evaluation BEFORE LLM
            AnswerConfidence preLlmConfidence = groundingEvaluator.evaluate(
                    searchResult.groundingScore(), budget.selectedChunks().size(), command.safetyMode());
            log.debug("Pre-LLM confidence={}", preLlmConfidence);

            // 5. Short-circuit: INSUFFICIENT + STRICT → no LLM call
            if (preLlmConfidence == AnswerConfidence.INSUFFICIENT
                    && command.safetyMode() == AssistantSafetyMode.STRICT) {
                log.warn("Short-circuit: INSUFFICIENT grounding in STRICT mode — fallback response");
                return buildFallbackResponse(command, trace, citations, preLlmConfidence, startMs);
            }

            // 6. Assemble retrieval context string
            String assembledContext = contextAssembler.assemble(budget.selectedChunks(), citations);

            // 7. Build versioned prompt (citations embedded inside system prompt)
            List<String> chunkIds = budget.selectedChunks().stream()
                    .map(SearchPort.RetrievedChunkDto::chunkId)
                    .collect(Collectors.toList());
            AssembledPrompt prompt = promptBuilder.build(
                    command.rawQuery(), assembledContext,
                    PromptVersion.V1_LEGAL_ID, retrievalSnapshotId, chunkIds);

            // 8. LLM invocation
            LlmRequest llmRequest = LlmRequest.strict(
                    prompt.systemPrompt(), prompt.userPrompt(), trace.traceId().toString());
            LlmResponse llmResponse = llmPort.invoke(llmRequest);
            log.debug("LLM response: stub={} tokens={}", llmResponse.isStub(), llmResponse.completionTokens());

            // 9. Unsupported claim detection (post-LLM)
            List<String> unsupportedClaims = claimDetector.detect(llmResponse.content(), citations);

            // 10. Hallucination guard
            GuardResult guardResult = hallucinationGuard.guard(
                    llmResponse.content(), preLlmConfidence, unsupportedClaims, command.safetyMode());
            log.debug("HallucinationGuard: passed={} downgraded={} warnings={}",
                    guardResult.passed(), guardResult.downgraded(), guardResult.warnings().size());

            // 11. Resolve final answer text
            String finalAnswer = guardResult.downgraded()
                    ? hallucinationGuard.getFallbackMessage()
                    : llmResponse.content();

            // 12. Follow-up suggestions
            List<FollowUpSuggestion> followUps = followUpService.suggest(command.rawQuery(), finalAnswer);

            // 13. Build structured response
            long processingMs = System.currentTimeMillis() - startMs;
            trace = trace.withProcessingMs(processingMs);
            AssistantResponse response = buildSuccessResponse(
                    command, trace, finalAnswer, citations, guardResult, followUps,
                    searchResult.groundingScore(), processingMs);

            // 14. Conversation memory
            memoryService.store(ConversationTurn.create(
                    command.sessionId(), command.tenantId(), command.userId(),
                    command.rawQuery(), finalAnswer,
                    guardResult.adjustedConfidence(), guardResult.hasWarnings(),
                    PromptVersion.V1_LEGAL_ID.version(), trace.traceId()));

            // 15. Audit
            auditPort.publishInteraction(buildAuditEvent(command, trace, guardResult, processingMs));

            // 16. Metrics
            metrics.recordInteractionCompleted(guardResult.adjustedConfidence(), processingMs);
            if (guardResult.hasWarnings()) metrics.recordHallucinationWarning();
            if (guardResult.downgraded())  metrics.recordDowngrade();

            log.info("Assistant done traceId={} confidence={} downgraded={} ms={}",
                    trace.traceId(), guardResult.adjustedConfidence(), guardResult.downgraded(), processingMs);

            return response;

        } catch (Exception e) {
            long processingMs = System.currentTimeMillis() - startMs;
            metrics.recordInteractionFailed();
            log.error("Assistant error traceId={}: {}", trace.traceId(), e.getMessage(), e);
            return AssistantResponse.error(trace.withProcessingMs(processingMs), e.getMessage());
        } finally {
            MDC.remove("traceId");
            MDC.remove("tenantId");
        }
    }

    private SearchPort.AssistantSearchRequest toSearchRequest(AssistantCommand command, UUID correlationId) {
        return new SearchPort.AssistantSearchRequest(
                command.rawQuery(),
                command.tenantId(),
                command.userId(),
                command.maxClassificationLevel().name(),
                command.documentTypeFilter() != null ? command.documentTypeFilter().name() : null,
                command.maxResults(),
                command.contextTokenBudget(),
                correlationId);
    }

    private AssistantResponse buildFallbackResponse(
            AssistantCommand command, ResponseTrace trace,
            List<CitationDto> citations, AnswerConfidence confidence, long startMs) {

        long processingMs = System.currentTimeMillis() - startMs;
        return AssistantResponse.success(
                trace.withProcessingMs(processingMs),
                hallucinationGuard.getFallbackMessage(),
                "(Tidak ada sumber yang dapat dikutip)",
                "INSUFFICIENT — tidak ada dokumen yang cukup untuk mendukung jawaban",
                List.of("Grounding tidak cukup untuk memberikan jawaban yang dapat dipercaya."),
                followUpService.suggest(command.rawQuery(), ""),
                confidence,
                0f,
                true,
                true,
                command.safetyMode(),
                citations,
                processingMs);
    }

    private AssistantResponse buildSuccessResponse(
            AssistantCommand command, ResponseTrace trace,
            String answerText, List<CitationDto> citations,
            GuardResult guardResult, List<FollowUpSuggestion> followUps,
            float groundingScore, long processingMs) {

        String citationSection = citationInjector.formatCitationBlock(citations);
        String confidenceSection = buildConfidenceSection(guardResult.adjustedConfidence(), groundingScore);
        List<String> followUpTexts = followUps.stream()
                .map(FollowUpSuggestion::questionText)
                .collect(Collectors.toList());

        return AssistantResponse.success(
                trace,
                answerText,
                citationSection,
                confidenceSection,
                guardResult.warnings(),
                followUpTexts,
                guardResult.adjustedConfidence(),
                groundingScore,
                guardResult.hasWarnings(),
                guardResult.downgraded(),
                command.safetyMode(),
                citations,
                processingMs);
    }

    private String buildConfidenceSection(AnswerConfidence confidence, float groundingScore) {
        return switch (confidence) {
            case HIGH         -> String.format("Tinggi (%.0f%%) — jawaban didukung kuat oleh dokumen.", groundingScore * 100);
            case MEDIUM       -> String.format("Sedang (%.0f%%) — jawaban cukup didukung; verifikasi mandiri dianjurkan.", groundingScore * 100);
            case LOW          -> String.format("Rendah (%.0f%%) — dokumen terbatas; konsultasikan dengan notaris.", groundingScore * 100);
            case INSUFFICIENT -> "Tidak cukup — tidak ada dokumen yang relevan ditemukan.";
        };
    }

    private AssistantAuditPort.AuditEvent buildAuditEvent(
            AssistantCommand command, ResponseTrace trace,
            GuardResult guardResult, long processingMs) {
        return new AssistantAuditPort.AuditEvent(
                trace.traceId(),
                command.sessionId(),
                command.tenantId(),
                command.userId(),
                command.rawQuery(),
                trace.promptVersion(),
                trace.retrievalSnapshotId(),
                guardResult.adjustedConfidence(),
                command.safetyMode(),
                guardResult.hasWarnings(),
                guardResult.downgraded(),
                processingMs);
    }
}
