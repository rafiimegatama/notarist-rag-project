package com.notarist.ingest.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.ingest.api.response.IngestionStatusResponse;
import com.notarist.ingest.api.response.UploadUrlResponse;
import com.notarist.ingest.application.command.InitiateIngestionCommand;
import com.notarist.ingest.application.port.in.GetIngestionStatusUseCase;
import com.notarist.ingest.application.port.in.InitiateIngestionUseCase;
import com.notarist.ingest.application.service.UploadOrchestrationService;
import com.notarist.ingest.domain.model.DocumentChecksum;
import com.notarist.ingest.domain.model.IngestionId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {

    private final InitiateIngestionUseCase initiateUseCase;
    private final GetIngestionStatusUseCase statusUseCase;
    private final UploadOrchestrationService uploadOrchestrationService;

    public IngestionController(
            InitiateIngestionUseCase initiateUseCase,
            GetIngestionStatusUseCase statusUseCase,
            UploadOrchestrationService uploadOrchestrationService) {
        this.initiateUseCase = initiateUseCase;
        this.statusUseCase = statusUseCase;
        this.uploadOrchestrationService = uploadOrchestrationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UploadUrlResponse>> initiateIngestion(
            @Valid @RequestBody InitiateRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader) {

        var principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));

        String correlationId = correlationHeader != null ? correlationHeader : UUID.randomUUID().toString();

        InitiateIngestionCommand command = new InitiateIngestionCommand(
                principal.tenantId(),
                principal.userId(),
                request.originalFilename(),
                new DocumentChecksum(request.checksumSha256()),
                JenisDokumen.valueOf(request.documentType()),
                ClassificationLevel.valueOf(request.classificationLevel()),
                new CorrelationId(correlationId));

        UploadUrlResponse response = initiateUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiMeta.of(correlationId), response));
    }

    @PostMapping("/{jobId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmUpload(
            @PathVariable UUID jobId,
            @Valid @RequestBody ConfirmRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader) {

        var principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));

        uploadOrchestrationService.confirmUpload(jobId, request.checksumSha256(), principal.tenantId());

        String correlationId = correlationHeader != null ? correlationHeader : UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId), null));
    }

    @GetMapping("/{ingestionId}/status")
    public ResponseEntity<ApiResponse<IngestionStatusResponse>> getStatus(
            @PathVariable UUID ingestionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationHeader) {

        var principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));

        IngestionStatusResponse response = statusUseCase.getStatus(
                IngestionId.of(ingestionId), principal.tenantId());

        String correlationId = correlationHeader != null ? correlationHeader : UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId), response));
    }

    public record InitiateRequest(
            @NotBlank String originalFilename,
            @NotBlank String checksumSha256,
            @NotNull String documentType,
            @NotNull String classificationLevel
    ) {}

    public record ConfirmRequest(
            @NotBlank String checksumSha256
    ) {}
}
