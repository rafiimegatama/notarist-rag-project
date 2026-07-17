package com.notarist.assistant.application.orchestrator;

import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.api.response.CitationDto;
import com.notarist.assistant.application.command.AssistantCommand;
import com.notarist.assistant.application.pipeline.CitationInjector;
import com.notarist.assistant.application.pipeline.ConversationMemoryService;
import com.notarist.assistant.application.pipeline.FollowUpSuggestionService;
import com.notarist.assistant.application.port.in.AssistantUseCase;
import com.notarist.assistant.application.port.out.AssistantAuditPort;
import com.notarist.assistant.domain.model.*;
import com.notarist.assistant.infrastructure.metrics.AssistantMetricsRegistry;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.search.application.routing.AnswerCitation;
import com.notarist.search.application.routing.AnswerRequest;
import com.notarist.search.application.routing.AnswerResult;
import com.notarist.search.application.routing.AnswerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The assistant's entry point. It asks the {@link AnswerRouter} for an answer and renders it.
 *
 * <p><b>What changed and why.</b> This class used to <em>be</em> the RAG pipeline: it held
 * {@code LlmPort} and {@code SearchPort} and ran retrieve → prompt → generate for every question that
 * arrived, including "berapa akta bulan ini". That made the language model the default execution
 * engine for the whole product — a model answering questions that have exact answers sitting in a
 * database. In a notary office an invented deed count or legal status is a liability, not a bug.
 *
 * <p>Now it depends on exactly one collaborator for answering: the router. It cannot see SQL, BM25,
 * the vector index or an LLM, and it has no way to choose between them. The routing decision belongs
 * to the router and is policed by {@code FactualQueryGuard}; this class renders whatever comes back.
 * The RAG pipeline still exists — behind {@code RagPort}, reachable only by strategies permitted to
 * use it. ArchUnit enforces this separation rather than leaving it to discipline.
 *
 * <p>Everything downstream of the answer — citations, confidence sections, memory, audit, metrics and
 * the response DTO — is unchanged, so the shipped mobile client sees an identical contract.
 */
@Service
public class AssistantOrchestrator implements AssistantUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssistantOrchestrator.class);

    private final AnswerRouter              answerRouter;
    private final CitationInjector          citationInjector;
    private final FollowUpSuggestionService followUpService;
    private final ConversationMemoryService memoryService;
    private final AssistantAuditPort        auditPort;
    private final AssistantMetricsRegistry  metrics;

    public AssistantOrchestrator(
            AnswerRouter answerRouter,
            CitationInjector citationInjector,
            FollowUpSuggestionService followUpService,
            ConversationMemoryService memoryService,
            AssistantAuditPort auditPort,
            AssistantMetricsRegistry metrics) {
        this.answerRouter     = answerRouter;
        this.citationInjector = citationInjector;
        this.followUpService  = followUpService;
        this.memoryService    = memoryService;
        this.auditPort        = auditPort;
        this.metrics          = metrics;
    }

    @Override
    public AssistantResponse ask(AssistantCommand command) {
        return execute(command, null);
    }

    @Override
    public AssistantResponse askStreaming(AssistantCommand command, StreamSink sink) {
        return execute(command, Objects.requireNonNull(sink, "sink"));
    }

    @Override
    public boolean cancelStream(String traceId) {
        return answerRouter.cancelStream(traceId);
    }

    private AssistantResponse execute(AssistantCommand command, StreamSink sink) {
        long startMs = System.currentTimeMillis();
        UUID retrievalSnapshotId = UUID.randomUUID();
        ResponseTrace trace = ResponseTrace.create(
                command.queryId(), command.sessionId(),
                PromptVersion.V1_LEGAL_ID.version(), retrievalSnapshotId);
        String traceIdStr = trace.traceId().toString();

        MDC.put("traceId",  traceIdStr);
        MDC.put("tenantId", command.tenantId().toString());

        if (sink != null) {
            // Opened before routing: a client that disconnects while the request is still queued
            // behind the single inference thread must not leave a doomed inference running.
            answerRouter.openStream(traceIdStr);
            sink.onStart(traceIdStr);
        }

        try {
            metrics.recordInteractionStarted(command.safetyMode());
            log.info("Assistant ask traceId={} tenantId={} query='{}'",
                    trace.traceId(), command.tenantId(), command.rawQuery());

            AnswerRequest request = toAnswerRequest(command, traceIdStr, retrievalSnapshotId);

            AnswerResult result = (sink != null)
                    ? answerRouter.routeStreaming(request, sink::onToken)
                    : answerRouter.route(request);

            long processingMs = System.currentTimeMillis() - startMs;
            trace = trace.withProcessingMs(processingMs);

            AssistantResponse response = toAssistantResponse(command, trace, result, processingMs);

            memoryService.store(ConversationTurn.create(
                    command.sessionId(), command.tenantId(), command.userId(),
                    command.rawQuery(), response.answerText(),
                    response.confidence(), response.hallucinationWarning(),
                    PromptVersion.V1_LEGAL_ID.version(), trace.traceId()));

            auditPort.publishInteraction(new AssistantAuditPort.AuditEvent(
                    trace.traceId(), command.sessionId(), command.tenantId(), command.userId(),
                    command.rawQuery(), trace.promptVersion(), trace.retrievalSnapshotId(),
                    response.confidence(), command.safetyMode(),
                    response.hallucinationWarning(), response.downgraded(), processingMs));

            metrics.recordInteractionCompleted(response.confidence(), processingMs);
            if (response.hallucinationWarning()) metrics.recordHallucinationWarning();
            if (response.downgraded())           metrics.recordDowngrade();

            AnswerResult.AnswerMetadata meta = result.metadata();
            log.info("Assistant done traceId={} strategy={} llmInvoked={} sqlInvoked={} docs={} citations={} ms={}",
                    trace.traceId(), meta.strategyUsed(), meta.llmInvoked(), meta.sqlInvoked(),
                    meta.documentsRetrieved(), meta.citationsCount(), meta.executionTimeMs());

            return response;

        } catch (Exception e) {
            long processingMs = System.currentTimeMillis() - startMs;
            metrics.recordInteractionFailed();
            log.error("Assistant error traceId={}: {}", trace.traceId(), e.getMessage(), e);
            return AssistantResponse.error(trace.withProcessingMs(processingMs), e.getMessage());
        } finally {
            if (sink != null) answerRouter.closeStream(traceIdStr);
            MDC.remove("traceId");
            MDC.remove("tenantId");
        }
    }

    private AnswerRequest toAnswerRequest(AssistantCommand command, String traceId, UUID snapshotId) {
        return new AnswerRequest(
                command.rawQuery(),
                command.tenantId(),
                command.userId(),
                command.maxClassificationLevel(),
                command.documentTypeFilter(),
                command.maxResults(),
                command.contextTokenBudget(),
                command.safetyMode() == AssistantSafetyMode.STRICT,
                traceId,
                CorrelationId.of(snapshotId.toString()));
    }

    /**
     * Renders any {@link AnswerResult} — SQL or RAG — into the existing response DTO. The shape is
     * unchanged, so the mobile client cannot tell (and does not need to know) which engine answered.
     */
    private AssistantResponse toAssistantResponse(
            AssistantCommand command, ResponseTrace trace, AnswerResult result, long processingMs) {

        List<CitationDto> citations = result.citations().stream()
                .map(this::toCitationDto)
                .collect(Collectors.toList());

        AnswerConfidence confidence = parseConfidence(result.confidence());

        List<String> followUps = followUpService.suggest(command.rawQuery(), result.answerText()).stream()
                .map(FollowUpSuggestion::questionText)
                .collect(Collectors.toList());

        return AssistantResponse.success(
                trace,
                result.answerText(),
                citations.isEmpty()
                        ? "(Tidak ada sumber yang dapat dikutip)"
                        : citationInjector.formatCitationBlock(citations),
                buildConfidenceSection(result, confidence),
                result.warnings(),
                followUps,
                confidence,
                result.groundingScore(),
                !result.warnings().isEmpty(),
                result.downgraded(),
                command.safetyMode(),
                citations,
                processingMs);
    }

    /**
     * A deterministic answer is not "strongly grounded in documents" — it is simply true. Describing
     * a SQL COUNT as "Tinggi (100%) — didukung dokumen" would misstate where the answer came from, so
     * factual answers get confidence text that names the database as the source and says plainly that
     * no AI was involved.
     */
    private String buildConfidenceSection(AnswerResult result, AnswerConfidence confidence) {
        if (result.metadata().sqlInvoked()) {
            return result.supported()
                    ? "Pasti — jawaban dihitung langsung dari basis data (bukan dari AI)."
                    : "Tidak tersedia — data yang dibutuhkan belum ada di sistem.";
        }
        float pct = result.groundingScore() * 100;
        return switch (confidence) {
            case HIGH         -> String.format("Tinggi (%.0f%%) — jawaban didukung kuat oleh dokumen.", pct);
            case MEDIUM       -> String.format("Sedang (%.0f%%) — jawaban cukup didukung; verifikasi mandiri dianjurkan.", pct);
            case LOW          -> String.format("Rendah (%.0f%%) — dokumen terbatas; konsultasikan dengan notaris.", pct);
            case INSUFFICIENT -> "Tidak cukup — tidak ada dokumen yang relevan ditemukan.";
        };
    }

    private AnswerConfidence parseConfidence(String raw) {
        if (raw == null) return AnswerConfidence.INSUFFICIENT;
        try {
            return AnswerConfidence.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AnswerConfidence.INSUFFICIENT;
        }
    }

    private CitationDto toCitationDto(AnswerCitation c) {
        return new CitationDto(
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
