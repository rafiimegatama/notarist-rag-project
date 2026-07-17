package com.notarist.search.application.routing;

/** The specific shape of a question within its {@link QueryCategory}. Selects the strategy. */
public enum QuerySubtype {

    // ---- FACTUAL -------------------------------------------------------------------------------
    /** "berapa akta bulan ini" — a single count/total. */
    STATISTICS(QueryCategory.FACTUAL),
    /** "berapa akta per jenis" — a grouped breakdown. */
    AGGREGATION(QueryCategory.FACTUAL),
    /** "SKMHT yang jatuh tempo minggu depan" — time-bounded obligations. */
    REMINDER(QueryCategory.FACTUAL),

    // ---- STATUS --------------------------------------------------------------------------------
    /** "apakah akta 125 sudah final" — lifecycle state of one entity. */
    STATUS_LOOKUP(QueryCategory.STATUS),
    /** "akta nomor 125/VII/2024" — exact identifier resolution. */
    IDENTIFIER_LOOKUP(QueryCategory.STATUS),

    // ---- SEMANTIC ------------------------------------------------------------------------------
    /** "klausul indemnity yang mirip" — similarity retrieval, ranked list. */
    SIMILARITY(QueryCategory.SEMANTIC),

    // ---- DOCUMENT INTELLIGENCE -----------------------------------------------------------------
    /** "ringkas dokumen ini". */
    SUMMARIZE(QueryCategory.DOCUMENT_INTELLIGENCE),
    /** "jelaskan klausul ini". */
    EXPLAIN(QueryCategory.DOCUMENT_INTELLIGENCE),
    /** "bandingkan dua dokumen ini". */
    COMPARISON(QueryCategory.DOCUMENT_INTELLIGENCE),

    // ---- FALLBACK ------------------------------------------------------------------------------
    /** Anything else — grounded RAG. */
    OPEN_QUESTION(QueryCategory.UNKNOWN);

    private final QueryCategory category;

    QuerySubtype(QueryCategory category) {
        this.category = category;
    }

    public QueryCategory category() {
        return category;
    }
}
