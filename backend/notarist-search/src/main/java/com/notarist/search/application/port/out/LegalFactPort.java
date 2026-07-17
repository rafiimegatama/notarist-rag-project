package com.notarist.search.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic facts read straight from the database. This is the only source permitted to answer a
 * count, a legal status, or a deadline.
 *
 * <p><b>Scope reality (important, and deliberately not hidden):</b> the Case/Bundle/Approval tables
 * do not exist yet — they arrive in a later sprint. So questions about bundle delivery, case
 * finalization or SKMHT expiry <em>cannot be answered</em> today. The correct response to those is
 * {@link FactAvailability#NOT_IMPLEMENTED}, which the router turns into an honest "not available
 * yet". It is emphatically NOT a reason to fall back to the LLM: a fluent, confident, invented
 * answer about whether a deed was signed is far worse than no answer.
 *
 * <p>Every query is tenant-scoped explicitly, in addition to Postgres RLS.
 */
public interface LegalFactPort {

    /** Total documents for a tenant within a time window. */
    CountFact countDocuments(UUID tenantId, TimeWindow window, String jenisAkta, String documentType);

    /** Documents whose ingestion failed (OCR/pipeline failure or DLQ). */
    CountFact countFailedDocuments(UUID tenantId, TimeWindow window);

    /** Grouped breakdown, e.g. count per jenis_akta or per status. */
    List<GroupCount> countGrouped(UUID tenantId, TimeWindow window, GroupBy groupBy);

    /** Resolves a single document by its nomor akta. Empty when not found. */
    List<DocumentFact> findByNomorAkta(UUID tenantId, String nomorAkta);

    /** Pipeline/lifecycle status of documents matching a nomor akta. */
    List<DocumentFact> findDocumentStatus(UUID tenantId, String nomorAkta);

    /** Time windows the classifier can extract. ALL = no time bound. */
    enum TimeWindow { TODAY, THIS_MONTH, THIS_YEAR, NEXT_WEEK, ALL }

    enum GroupBy { JENIS_AKTA, STATUS, DOCUMENT_TYPE }

    /**
     * Whether the backing data exists. AVAILABLE = answered from real tables.
     * NOT_IMPLEMENTED = the question is legitimate but the schema for it does not exist yet.
     */
    enum FactAvailability { AVAILABLE, NOT_IMPLEMENTED }

    record CountFact(long count, FactAvailability availability, String detail) {
        public static CountFact of(long count) {
            return new CountFact(count, FactAvailability.AVAILABLE, null);
        }
        public static CountFact notImplemented(String detail) {
            return new CountFact(0, FactAvailability.NOT_IMPLEMENTED, detail);
        }
    }

    record GroupCount(String key, long count) {}

    record DocumentFact(
            String documentId,
            String documentTitle,
            String nomorAkta,
            String jenisAkta,
            String documentType,
            String status,
            String classificationLevel,
            String createdAt,
            String indexedAt
    ) {}
}
