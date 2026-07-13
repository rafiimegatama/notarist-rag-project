package com.notarist.audit.infrastructure.event;

import com.notarist.audit.domain.model.AuditEntry;
import com.notarist.audit.domain.model.AuditEventType;
import com.notarist.audit.domain.model.AuditOutcome;
import com.notarist.core.api.audit.AuditEventPayload;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Pure mapping/classification logic of the F9 audit listener — no Spring, no database. */
class AuditEventListenerTest {

    private static AuditEventPayload payload(String eventType, String outcome, String correlationId) {
        return new AuditEventPayload(
                eventType, "USER", "subject-1", UUID.randomUUID(), "NOTARIS",
                UUID.randomUUID(), "LOGIN", outcome, "10.0.0.1", correlationId,
                Map.of("username", "budi"));
    }

    @Test
    void mapsKnownEventTypeAndOutcome() {
        AuditEntry entry = AuditEventListener.toEntry(payload("AUTH_LOGIN_SUCCESS", "SUCCESS", "corr-1"));

        assertEquals(AuditEventType.AUTH_LOGIN_SUCCESS, entry.getEventType());
        assertEquals("AUTH", entry.getEventType().getCategory());
        assertEquals(AuditOutcome.SUCCESS, entry.getOutcome());
        assertEquals("corr-1", entry.getCorrelationId().value());
        assertEquals("10.0.0.1", entry.getIpAddress());
        assertNotNull(entry.getAuditId());
    }

    @Test
    void everyEventTypeEmittedByExistingPublishersResolvesToARealConstant() {
        // These are the literals actually published by notarist-auth, notarist-document and
        // notarist-ingest. None may fall through to UNMAPPED, or the trail would be lossy.
        String[] emitted = {
                "AUTH_LOGIN_SUCCESS", "AUTH_LOGIN_FAILURE", "AUTH_TOKEN_REFRESH", "AUTH_LOGOUT",
                "DOCUMENT_ACCESS", "DOCUMENT_LIST", "DOCUMENT_UPLOAD",
                "INGEST_UPLOAD_INITIATED", "INGEST_UPLOAD_CONFIRMED", "INGEST_STAGE_COMPLETED",
                "INGEST_STAGE_FAILED", "INGEST_MOVED_TO_DLQ", "INGEST_PIPELINE_COMPLETED"
        };
        for (String raw : emitted) {
            assertEquals(raw, AuditEventType.resolve(raw).name(),
                    "publisher emits '" + raw + "' but the registry does not round-trip it");
        }
    }

    @Test
    void unknownEventTypeIsPersistedAsUnmappedWithRawStringPreserved() {
        AuditEntry entry = AuditEventListener.toEntry(payload("SOME_FUTURE_EVENT", "SUCCESS", "corr-2"));

        assertEquals(AuditEventType.UNMAPPED, entry.getEventType());
        assertEquals("UNKNOWN", entry.getEventType().getCategory());
        assertEquals("SOME_FUTURE_EVENT", entry.getDetailJson().get("sourceEventType"));
        assertEquals("budi", entry.getDetailJson().get("username"));  // original detail retained
    }

    @Test
    void unknownOutcomeDegradesToPartialRatherThanDroppingTheEvent() {
        AuditEntry entry = AuditEventListener.toEntry(payload("AUTH_LOGOUT", "WEIRD", "corr-3"));
        assertEquals(AuditOutcome.PARTIAL, entry.getOutcome());
    }

    @Test
    void blankCorrelationIdBecomesNullRatherThanThrowing() {
        // CorrelationId's compact constructor rejects blank values; the mapper must guard.
        AuditEntry entry = AuditEventListener.toEntry(payload("AUTH_LOGOUT", "SUCCESS", null));
        assertNull(entry.getCorrelationId());
    }

    @Test
    void complianceCriticalCategoriesAreFailClosedAndBackgroundOnesAreNot() {
        assertTrue(AuditEventType.AUTH_LOGIN_FAILURE.isFailClosed());
        assertTrue(AuditEventType.SECURITY_ACCESS_DENIED.isFailClosed());
        assertTrue(AuditEventType.DOCUMENT_ACCESS.isFailClosed());
        assertTrue(AuditEventType.UNMAPPED.isFailClosed());  // unknown → treat as sensitive

        assertFalse(AuditEventType.INGEST_STAGE_FAILED.isFailClosed());
        assertFalse(AuditEventType.SEARCH_HYBRID_EXECUTED.isFailClosed());
        assertFalse(AuditEventType.AI_QUERY_SUBMITTED.isFailClosed());
    }
}
