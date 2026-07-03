package com.notarist.search.api.request;

import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.search.domain.model.SearchIntent;

public record SearchRequest(
        String rawQuery,
        ClassificationLevel maxClassificationLevel,
        JenisDokumen documentTypeFilter,
        SearchIntent intentOverride,
        Integer maxResults,
        Integer contextTokenBudget
) {}
