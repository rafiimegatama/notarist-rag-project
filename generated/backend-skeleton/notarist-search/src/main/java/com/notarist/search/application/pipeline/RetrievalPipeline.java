package com.notarist.search.application.pipeline;

import com.notarist.search.domain.model.RetrievalResult;
import com.notarist.search.domain.model.SearchQuery;

import java.util.List;

/**
 * Strategy interface for modular retrieval pipelines.
 * Implementations: HybridRetrievalPipeline, RegulationRetrievalPipeline, SopRetrievalPipeline.
 * Each implementation is pluggable and selected based on SearchQuery.intent.
 */
public interface RetrievalPipeline {

    boolean supports(SearchQuery query);

    List<RetrievalResult> retrieve(SearchQuery query);
}
