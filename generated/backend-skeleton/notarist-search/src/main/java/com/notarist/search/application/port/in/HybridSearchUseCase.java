package com.notarist.search.application.port.in;

import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.domain.model.SearchQuery;

public interface HybridSearchUseCase {
    SearchResponse execute(SearchQuery query);
}
