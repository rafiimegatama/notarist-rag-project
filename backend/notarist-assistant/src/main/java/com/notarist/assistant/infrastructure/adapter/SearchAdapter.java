package com.notarist.assistant.infrastructure.adapter;

import com.notarist.assistant.application.port.out.SearchPort;
import com.notarist.assistant.domain.model.AnswerConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Search adapter stub — Phase 4.
 *
 * Real integration with notarist-search module (SearchUseCase) deferred to Phase 5.
 * Returns empty SearchResult so the assistant pipeline exercises the INSUFFICIENT
 * grounding path and fallback response, validating the full guard chain.
 *
 * In Phase 5 this adapter will call SearchUseCase directly (in-process) or
 * via the search REST API, translating SearchResponse → SearchPort.SearchResult.
 */
@Component
public class SearchAdapter implements SearchPort {

    private static final Logger log = LoggerFactory.getLogger(SearchAdapter.class);

    @Override
    public SearchResult search(AssistantSearchRequest request) {
        log.debug("SearchAdapter stub — Phase 4, returning empty. tenantId={} query='{}'",
                request.tenantId(), request.query());
        return SearchResult.empty();
    }
}
