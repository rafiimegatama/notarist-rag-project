package com.notarist.search.api.rest;

import com.notarist.core.api.response.ApiMeta;
import com.notarist.core.api.response.ApiResponse;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.search.api.request.SearchRequest;
import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.port.in.SearchUseCase;
import com.notarist.search.application.query.SearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * Tenant and user identity are resolved from mandatory headers (set by API gateway
     * or auth filter). Classification level defaults to INTERNAL when not specified.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestBody SearchRequest request,
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader) {

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

        ApiResponse<SearchResponse> apiResponse = "SUCCESS".equals(response.status())
                ? ApiResponse.success(ApiMeta.of(correlationId.value()), response)
                : ApiResponse.error(ApiMeta.of(correlationId.value()), "SEARCH_FAILED", response.errorMessage());

        return ResponseEntity.ok(apiResponse);
    }
}
