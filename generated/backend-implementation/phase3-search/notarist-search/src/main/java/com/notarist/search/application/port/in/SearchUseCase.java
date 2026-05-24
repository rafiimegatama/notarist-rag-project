package com.notarist.search.application.port.in;

import com.notarist.search.api.response.SearchResponse;
import com.notarist.search.application.query.SearchQuery;

public interface SearchUseCase {
    SearchResponse search(SearchQuery query);
}
