package com.notarist.search.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.security.VpdContextHolder;
import com.notarist.search.api.request.SearchRequest;
import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.port.in.SearchUseCase;
import com.notarist.search.application.query.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchUseCase searchUseCase;

    public SearchController(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    /**
     * POST /api/v1/search
     *
     * Tenant and user identity are resolved from the authenticated principal
     * (VpdContextHolder, populated by JwtAuthenticationFilter from the bearer token).
     * Classification level defaults to INTERNAL when not specified.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestBody SearchRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

        VpdContextHolder.VpdPrincipal principal = VpdContextHolder.get()
                .orElseThrow(() -> new IllegalStateException("Unauthenticated request"));
        UUID tenantId = principal.tenantId();
        UUID userId = principal.userId();

        CorrelationId correlationId = correlationIdHeader != null && !correlationIdHeader.isBlank()
                ? CorrelationId.of(correlationIdHeader)
                : CorrelationId.generate();

        ClassificationLevel maxLevel = request.maxClassificationLevel() != null
                ? request.maxClassificationLevel()
                : ClassificationLevel.INTERNAL;

        int maxResults       = (request.maxResults() != null && request.maxResults() > 0)
                ? request.maxResults() : 10;
        int contextBudget    = (request.contextTokenBudget() != null && request.contextTokenBudget() > 0)
                ? request.contextTokenBudget() : 4096;

        SearchQuery query = new SearchQuery(
                UUID.randomUUID(),
                request.rawQuery(),
                tenantId,
                userId,
                maxLevel,
                request.documentTypeFilter(),
                request.intentOverride(),
                maxResults,
                contextBudget,
                correlationId);

        log.info("Search request queryId={} tenantId={} rawQuery='{}'",
                query.queryId(), tenantId, request.rawQuery());

        SearchResponse response = searchUseCase.search(query);

        if ("SUCCESS".equals(response.status())) {
            return ResponseEntity.ok(ApiResponse.success(ApiMeta.of(correlationId.value()), response));
        }

        // A failed search must not be an HTTP 200.
        //
        // This previously built the ERROR envelope and then returned it inside ResponseEntity.ok(),
        // so a caller that checks the status code — which is every HTTP client, cache and proxy —
        // saw 200 OK for "qdrant.search failed after 3 attempts". The failure was only visible to a
        // caller that ignored the status and parsed the body, and it never counted toward the 5xx
        // error-rate alert in modules/monitoring, so a fully broken vector backend looked healthy.
        log.warn("Search failed queryId={} correlationId={}: {}",
                query.queryId(), correlationId.value(), response.errorMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        ApiMeta.of(correlationId.value()), "SEARCH_FAILED", response.errorMessage()));
    }
}
