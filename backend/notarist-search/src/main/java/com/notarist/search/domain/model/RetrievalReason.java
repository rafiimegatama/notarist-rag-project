package com.notarist.search.domain.model;

/** Explains why a chunk was included in retrieval results. Every result must carry at least one. */
public enum RetrievalReason {
    KEYWORD_MATCH,
    SEMANTIC_MATCH,
    RERANK_BOOST,
    LEGAL_REFERENCE,
    RELATED_DOCUMENT,
    CITATION_CHAIN;
}
