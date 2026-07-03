package com.notarist.document.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.api.response.PageResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.core.util.NotaristConstants;
import com.notarist.document.api.response.DocumentLegalResponse;
import com.notarist.document.application.handler.query.GetDocumentQueryHandler;
import com.notarist.document.application.handler.query.ListDocumentsQueryHandler;
import com.notarist.document.application.query.GetDocumentQuery;
import com.notarist.document.application.query.ListDocumentsQuery;
import com.notarist.document.domain.model.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(NotaristConstants.API_BASE_PATH + "/documents")
@Tag(name = "Documents", description = "Legal document access and metadata")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final GetDocumentQueryHandler getDocumentHandler;
    private final ListDocumentsQueryHandler listDocumentsHandler;

    public DocumentController(
            GetDocumentQueryHandler getDocumentHandler,
            ListDocumentsQueryHandler listDocumentsHandler) {
        this.getDocumentHandler = getDocumentHandler;
        this.listDocumentsHandler = listDocumentsHandler;
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Retrieve a single legal document by ID")
    public ResponseEntity<ApiResponse<DocumentLegalResponse>> getDocument(
            @PathVariable UUID documentId,
            HttpServletRequest request) {

        VpdContextHolder.VpdPrincipal principal = requirePrincipal();
        CorrelationId correlationId = extractCorrelationId(request);

        DocumentLegalResponse response = getDocumentHandler.handle(new GetDocumentQuery(
                new DocumentId(documentId),
                principal.userId(),
                principal.highestRole(),
                principal.tenantId(),
                correlationId
        ));

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
    }

    @GetMapping
    @Operation(summary = "List legal documents for the caller's tenant with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<DocumentLegalResponse>>> listDocuments(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        VpdContextHolder.VpdPrincipal principal = requirePrincipal();
        CorrelationId correlationId = extractCorrelationId(request);

        JenisDokumen typeFilter = documentType != null ? JenisDokumen.valueOf(documentType) : null;
        DocumentStatus statusFilter = status != null ? DocumentStatus.valueOf(status) : null;
        ClassificationLevel callerClearance = resolveCallerClearance(principal.highestRole());

        PageResponse<DocumentLegalResponse> pageResponse = listDocumentsHandler.handle(
                new ListDocumentsQuery(
                        principal.tenantId(),
                        principal.userId(),
                        principal.highestRole(),
                        callerClearance,
                        typeFilter,
                        statusFilter,
                        page,
                        Math.min(size, NotaristConstants.MAX_PAGE_SIZE),
                        correlationId
                ));

        return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), pageResponse));
    }

    private VpdContextHolder.VpdPrincipal requirePrincipal() {
        return VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal in context"));
    }

    private CorrelationId extractCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(NotaristConstants.HEADER_CORRELATION_ID);
        return (header != null && !header.isBlank()) ? CorrelationId.of(header) : CorrelationId.generate();
    }

    private ClassificationLevel resolveCallerClearance(String roleName) {
        return switch (roleName) {
            case "ADMIN", "PIMPINAN" -> ClassificationLevel.STRICTLY_CONFIDENTIAL;
            case "NOTARIS", "PPAT_OFFICER" -> ClassificationLevel.CONFIDENTIAL;
            default -> ClassificationLevel.INTERNAL;
        };
    }
}
