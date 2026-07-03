package com.notarist.search.domain.model;

/** Classified user intent — drives retrieval strategy and result ranking. */
public enum SearchIntent {
    DOCUMENT_LOOKUP,
    REGULATION_LOOKUP,
    SEMANTIC_QUESTION,
    RELATED_DOCUMENT,
    CITATION_LOOKUP;
}
