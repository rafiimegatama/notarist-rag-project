package com.notarist.assistant.api.rest;

import com.notarist.assistant.api.request.AssistantRequest;
import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.api.response.SseEvent;
import com.notarist.assistant.application.command.AssistantCommand;
import com.notarist.assistant.application.pipeline.ConversationMemoryService;
import com.notarist.assistant.application.pipeline.ResponseStreamer;
import com.notarist.assistant.application.port.in.AssistantUseCase;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import com.notarist.assistant.domain.model.ConversationTurn;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.core.security.VpdContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private static final ExecutorService SSE_POOL = Executors.newCachedThreadPool(
            r -> { Thread t = new Thread(r, "sse-streamer"); t.setDaemon(true); return t; });

    private final AssistantUseCase  assistantUseCase;
    private final ResponseStreamer  responseStreamer;
    private final ConversationMemoryService conversationMemoryService;

    public AssistantController(
            AssistantUseCase assistantUseCase,
            ResponseStreamer responseStreamer,
            ConversationMemoryService conversationMemoryService) {
        this.assistantUseCase = assistantUseCase;
        this.responseStreamer  = responseStreamer;
        this.conversationMemoryService = conversationMemoryService;
    }

    /**
     * POST /api/v1/assistant/ask
     * Synchronous — returns full AssistantResponse wrapped in ApiResponse.
     */
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<AssistantResponse>> ask(
            @RequestBody AssistantRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        VpdContextHolder.VpdPrincipal principal = requirePrincipal();
        CorrelationId correlationId = resolveCorrelationId(correlationIdHeader);
        AssistantCommand command = buildCommand(request, principal.tenantId(), principal.userId());

        log.info("Assistant ask tenantId={} queryId={}", principal.tenantId(), command.queryId());

        AssistantResponse response = assistantUseCase.ask(command);
        ApiResponse<AssistantResponse> apiResponse = "SUCCESS".equals(response.status())
                ? ApiResponse.success(ApiMeta.of(correlationId.value()), response)
                : ApiResponse.error(ApiMeta.of(correlationId.value()), "ASSISTANT_FAILED", response.errorMessage());

        return ResponseEntity.ok(apiResponse);
    }

    /**
     * POST /api/v1/assistant/ask/stream
     * SSE streaming — emits ANSWER_TOKEN, CITATION, CONFIDENCE, WARNING, FOLLOW_UP, DONE events.
     * Timeout: 60 seconds per request.
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            @RequestBody AssistantRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        VpdContextHolder.VpdPrincipal principal = requirePrincipal();
        SseEmitter emitter = new SseEmitter(60_000L);
        AssistantCommand command = buildCommand(request, principal.tenantId(), principal.userId());

        log.info("Assistant stream tenantId={} queryId={}", principal.tenantId(), command.queryId());

        SSE_POOL.submit(() -> {
            try {
                AssistantResponse response = assistantUseCase.ask(command);
                responseStreamer.stream(response, emitter);
            } catch (Exception e) {
                log.error("SSE error queryId={}: {}", command.queryId(), e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("ERROR")
                            .data(SseEvent.error(e.getMessage(), null, 0)));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * GET /api/v1/assistant/history/{sessionId}
     * Returns the caller's tenant-scoped conversation turns for a session (in-memory, see ConversationMemoryService).
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<ApiResponse<List<ConversationTurn>>> history(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        VpdContextHolder.VpdPrincipal principal = requirePrincipal();
        CorrelationId correlationId = resolveCorrelationId(correlationIdHeader);

        List<ConversationTurn> turns = conversationMemoryService.getHistory(sessionId).stream()
                .filter(turn -> turn.tenantId().equals(principal.tenantId()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), turns));
    }

    private VpdContextHolder.VpdPrincipal requirePrincipal() {
        return VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));
    }

    private AssistantCommand buildCommand(AssistantRequest request, UUID tenantId, UUID userId) {
        ClassificationLevel level = parseEnum(request.maxClassificationLevel(),
                ClassificationLevel.class, ClassificationLevel.INTERNAL);
        JenisDokumen docType = parseEnum(request.documentTypeFilter(),
                JenisDokumen.class, null);
        AssistantSafetyMode safetyMode = parseEnum(request.safetyMode(),
                AssistantSafetyMode.class, AssistantSafetyMode.STRICT);

        int maxResults    = (request.maxResults() != null && request.maxResults() > 0) ? request.maxResults() : 10;
        int tokenBudget   = (request.contextTokenBudget() != null && request.contextTokenBudget() > 0)
                ? request.contextTokenBudget() : 3072;
        UUID sessionId    = parseUuid(request.sessionId());

        return new AssistantCommand(
                UUID.randomUUID(), sessionId, tenantId, userId,
                request.rawQuery(), level, docType, safetyMode,
                maxResults, tokenBudget);
    }

    private CorrelationId resolveCorrelationId(String header) {
        return (header != null && !header.isBlank()) ? CorrelationId.of(header) : CorrelationId.generate();
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, T defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
