package com.notarist.assistant.api.rest;

import com.notarist.assistant.api.request.AssistantRequest;
import com.notarist.assistant.api.response.AssistantResponse;
import com.notarist.assistant.api.response.SseEvent;
import com.notarist.assistant.application.command.AssistantCommand;
import com.notarist.assistant.application.pipeline.ResponseStreamer;
import com.notarist.assistant.application.port.in.AssistantUseCase;
import com.notarist.assistant.domain.model.AssistantSafetyMode;
import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    public AssistantController(AssistantUseCase assistantUseCase, ResponseStreamer responseStreamer) {
        this.assistantUseCase = assistantUseCase;
        this.responseStreamer  = responseStreamer;
    }

    /**
     * POST /api/v1/assistant/ask
     * Synchronous — returns full AssistantResponse wrapped in ApiResponse.
     */
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<AssistantResponse>> ask(
            @RequestBody AssistantRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        CorrelationId correlationId = resolveCorrelationId(correlationIdHeader);
        AssistantCommand command = buildCommand(request, tenantId, userId, sessionId);

        log.info("Assistant ask tenantId={} queryId={}", tenantId, command.queryId());

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
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        SseEmitter emitter = new SseEmitter(60_000L);
        AssistantCommand command = buildCommand(request, tenantId, userId, sessionId);

        log.info("Assistant stream tenantId={} queryId={}", tenantId, command.queryId());

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

    private AssistantCommand buildCommand(AssistantRequest request, UUID tenantId, UUID userId, UUID sessionId) {
        ClassificationLevel level = parseEnum(request.maxClassificationLevel(),
                ClassificationLevel.class, ClassificationLevel.INTERNAL);
        JenisDokumen docType = parseEnum(request.documentTypeFilter(),
                JenisDokumen.class, null);
        AssistantSafetyMode safetyMode = parseEnum(request.safetyMode(),
                AssistantSafetyMode.class, AssistantSafetyMode.STRICT);

        int maxResults    = (request.maxResults() != null && request.maxResults() > 0) ? request.maxResults() : 10;
        int tokenBudget   = (request.contextTokenBudget() != null && request.contextTokenBudget() > 0)
                ? request.contextTokenBudget() : 3072;

        return new AssistantCommand(
                UUID.randomUUID(), sessionId, tenantId, userId,
                request.rawQuery(), level, docType, safetyMode,
                maxResults, tokenBudget);
    }

    private CorrelationId resolveCorrelationId(String header) {
        return (header != null && !header.isBlank()) ? CorrelationId.of(header) : CorrelationId.generate();
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
