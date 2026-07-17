package com.notarist.search.application.routing;

import java.util.Map;

/**
 * The classifier's verdict: what kind of question this is, plus any parameters lifted out of the
 * raw text (a nomor akta, a time window, a document type).
 *
 * <p>Parameters are extracted deterministically by regex, never by a model — the whole point of the
 * factual path is that no probabilistic component touches the value that ends up in a SQL predicate.
 */
public record ClassifiedQuery(
        QuerySubtype subtype,
        String rawQuery,
        Map<String, String> parameters
) {
    /** Well-known parameter keys. */
    public static final String P_NOMOR_AKTA    = "nomorAkta";
    public static final String P_DOCUMENT_TYPE = "documentType";
    public static final String P_JENIS_AKTA    = "jenisAkta";
    public static final String P_TIME_WINDOW   = "timeWindow";   // THIS_MONTH | TODAY | NEXT_WEEK | ALL
    public static final String P_GROUP_BY      = "groupBy";      // JENIS_AKTA | STATUS | DOCUMENT_TYPE
    public static final String P_STATUS_TOPIC  = "statusTopic";  // OCR_FAILURE | DOCUMENT | CASE_LIFECYCLE
    /** "true" when the user asked to SEE documents ("tampilkan/daftar") rather than have them explained. */
    public static final String P_WANTS_LIST    = "wantsList";

    public ClassifiedQuery {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public QueryCategory category() {
        return subtype.category();
    }

    public String param(String key) {
        return parameters.get(key);
    }

    public String paramOr(String key, String fallback) {
        return parameters.getOrDefault(key, fallback);
    }

    public boolean isLlmEligible() {
        return category().isLlmEligible();
    }
}
