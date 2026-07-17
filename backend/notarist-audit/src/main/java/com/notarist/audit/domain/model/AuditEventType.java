package com.notarist.audit.domain.model;

/**
 * Audit event type registry.
 *
 * The original STEP 7.5 contract froze 23 types across 6 categories. Those 23 are
 * retained verbatim below. F9 remediation (2026-07-13) added the 6 types that the
 * existing publishers were already emitting but that had no constant here
 * (DOCUMENT_ACCESS, DOCUMENT_LIST, DOCUMENT_UPLOAD, INGEST_UPLOAD_INITIATED,
 * INGEST_MOVED_TO_DLQ, INGEST_PIPELINE_COMPLETED) plus UNMAPPED.
 *
 * Rationale: {@link #resolve(String)} must be total. If an emitted event type had no
 * constant, the listener would either throw (dropping a compliance record) or have to
 * lump it into an unrelated type (falsifying the trail). Adding the constants keeps
 * event_type in the database exactly equal to what the publisher emitted, so the trail
 * stays filterable and truthful. UNMAPPED is the last-resort bucket for a *future*
 * publisher string nobody registered here — such an event is still persisted, with the
 * original string preserved in detail_json.sourceEventType, never dropped.
 */
public enum AuditEventType {

    // DOCUMENT category
    DOCUMENT_VIEWED,
    DOCUMENT_CHUNK_VIEWED,
    DOCUMENT_DOWNLOAD_URL_GENERATED,
    DOCUMENT_METADATA_UPDATED,
    DOCUMENT_ACCESS,
    DOCUMENT_LIST,
    DOCUMENT_UPLOAD,

    // INGEST category
    INGEST_JOB_INITIATED,
    INGEST_UPLOAD_INITIATED,
    INGEST_UPLOAD_CONFIRMED,
    INGEST_STAGE_COMPLETED,
    INGEST_STAGE_FAILED,
    INGEST_JOB_DLQ,
    INGEST_MOVED_TO_DLQ,
    INGEST_PIPELINE_COMPLETED,

    // SEARCH category
    SEARCH_HYBRID_EXECUTED,
    SEARCH_SEMANTIC_EXECUTED,

    // ASSISTANT category
    AI_SESSION_CREATED,
    AI_QUERY_SUBMITTED,
    AI_RESPONSE_GENERATED,
    AI_CITATION_CREATED,
    AI_HALLUCINATION_FLAGGED,

    // AUTH category
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_TOKEN_REFRESH,
    AUTH_LOGOUT,

    // SECURITY category
    SECURITY_ACCESS_DENIED,
    SECURITY_VPD_BLOCKED,
    SENSITIVE_FIELD_ACCESSED,

    // Fallback — an event type emitted by a publisher that is not registered above.
    // Persisted, never dropped; the raw string is kept in detail_json.sourceEventType.
    UNMAPPED;

    /** Total resolution of a publisher-supplied event-type string. Never throws. */
    public static AuditEventType resolve(String raw) {
        if (raw == null || raw.isBlank()) return UNMAPPED;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNMAPPED;
        }
    }

    public String getCategory() {
        return switch (this) {
            case DOCUMENT_VIEWED, DOCUMENT_CHUNK_VIEWED,
                 DOCUMENT_DOWNLOAD_URL_GENERATED, DOCUMENT_METADATA_UPDATED,
                 DOCUMENT_ACCESS, DOCUMENT_LIST, DOCUMENT_UPLOAD -> "DOCUMENT";
            case INGEST_JOB_INITIATED, INGEST_UPLOAD_INITIATED, INGEST_UPLOAD_CONFIRMED,
                 INGEST_STAGE_COMPLETED, INGEST_STAGE_FAILED, INGEST_JOB_DLQ,
                 INGEST_MOVED_TO_DLQ, INGEST_PIPELINE_COMPLETED -> "INGEST";
            case SEARCH_HYBRID_EXECUTED, SEARCH_SEMANTIC_EXECUTED -> "SEARCH";
            case AI_SESSION_CREATED, AI_QUERY_SUBMITTED,
                 AI_RESPONSE_GENERATED, AI_CITATION_CREATED, AI_HALLUCINATION_FLAGGED -> "ASSISTANT";
            case AUTH_LOGIN_SUCCESS, AUTH_LOGIN_FAILURE,
                 AUTH_TOKEN_REFRESH, AUTH_LOGOUT -> "AUTH";
            case SECURITY_ACCESS_DENIED, SECURITY_VPD_BLOCKED, SENSITIVE_FIELD_ACCESSED -> "SECURITY";
            case UNMAPPED -> "UNKNOWN";
        };
    }

    /**
     * Whether a failure to persist this event must fail the business operation
     * (fail-closed) rather than be logged and swallowed (fail-open).
     *
     * AUTH / SECURITY / DOCUMENT events are the regulated access trail for a notary/PPAT
     * office: an unauditable authentication or a confidential-document read must not be
     * allowed to succeed silently. INGEST / SEARCH / ASSISTANT events are background or
     * telemetry-grade; the ingestion pipeline already has retry + DLQ, and failing a
     * pipeline stage over an audit-store blip would escalate a transient fault into
     * dead-lettered documents.
     */
    public boolean isFailClosed() {
        return switch (getCategory()) {
            case "AUTH", "SECURITY", "DOCUMENT", "UNKNOWN" -> true;
            default -> false;
        };
    }
}
