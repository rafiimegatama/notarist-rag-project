package com.notarist.document.application.handler.query;

import com.notarist.core.api.audit.AuditEventPayload;
import com.notarist.core.domain.exception.DocumentNotFoundException;
import com.notarist.core.domain.exception.UnauthorizedAccessException;
import com.notarist.core.domain.valueobject.ClassificationLevel;
import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.JenisDokumen;
import com.notarist.document.application.port.out.DocumentLegalRepository;
import com.notarist.document.application.query.GetDocumentQuery;
import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.infrastructure.event.DocumentAuditEventPublisher;
import com.notarist.document.infrastructure.persistence.mapper.DocumentLegalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Denial paths on document read.
 *
 * <p>Both a cross-tenant attempt and an insufficient-clearance attempt used to throw and leave NO
 * audit trace: the trail recorded who successfully read a confidential document, but not who was
 * turned away from one — exactly the record a notary/PPAT compliance review asks for.
 */
class GetDocumentQueryHandlerTest {

    private static final UUID CALLER_TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final DocumentId DOC_ID = DocumentId.generate();

    private DocumentLegalRepository repository;
    private ApplicationEventPublisher events;
    private GetDocumentQueryHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentLegalRepository.class);
        events = mock(ApplicationEventPublisher.class);
        handler = new GetDocumentQueryHandler(
                repository, new DocumentLegalMapper(), new DocumentAuditEventPublisher(events));
    }

    private static DocumentLegal document(UUID tenantId, ClassificationLevel level) {
        return new DocumentLegal(
                DOC_ID, "Akta Jual Beli No. 12", JenisDokumen.AKTA, null, null, null,
                level, "notarist-raw/key", "checksum", 1024L, "application/pdf",
                UUID.randomUUID(), tenantId, UUID.randomUUID());
    }

    private static GetDocumentQuery query(String role) {
        return new GetDocumentQuery(DOC_ID, CALLER_ID, role, CALLER_TENANT, CorrelationId.generate());
    }

    private AuditEventPayload singlePublishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        return (AuditEventPayload) captor.getValue();
    }

    @Test
    @DisplayName("a cross-tenant read is denied, audited, and reported as NOT_FOUND")
    void crossTenantReadIsDeniedAndAudited() {
        when(repository.findById(DOC_ID))
                .thenReturn(Optional.of(document(OTHER_TENANT, ClassificationLevel.INTERNAL)));

        // NOT_FOUND, not FORBIDDEN: the caller must not learn the document exists elsewhere.
        assertThatThrownBy(() -> handler.handle(query("NOTARIS")))
                .isInstanceOf(DocumentNotFoundException.class);

        AuditEventPayload denial = singlePublishedEvent();
        assertThat(denial.eventType()).isEqualTo("SECURITY_ACCESS_DENIED");
        assertThat(denial.outcome()).isEqualTo("FAILURE");
        assertThat(denial.detail()).containsEntry("reason", "CROSS_TENANT_ACCESS");
        assertThat(denial.actorUserId()).isEqualTo(CALLER_ID);
        assertThat(denial.tenantId())
                .as("the event belongs to the tenant that probed, not the tenant probed")
                .isEqualTo(CALLER_TENANT);
    }

    @Test
    @DisplayName("insufficient clearance is denied and audited")
    void insufficientClearanceIsDeniedAndAudited() {
        // STAFF clears INTERNAL; the document is STRICTLY_CONFIDENTIAL.
        when(repository.findById(DOC_ID))
                .thenReturn(Optional.of(document(CALLER_TENANT, ClassificationLevel.STRICTLY_CONFIDENTIAL)));

        assertThatThrownBy(() -> handler.handle(query("STAFF")))
                .isInstanceOf(UnauthorizedAccessException.class);

        AuditEventPayload denial = singlePublishedEvent();
        assertThat(denial.eventType()).isEqualTo("SECURITY_ACCESS_DENIED");
        assertThat(denial.detail()).containsEntry("reason", "INSUFFICIENT_CLEARANCE");
        assertThat(denial.actorRole()).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("a permitted read still audits DOCUMENT_ACCESS, not a denial")
    void permittedReadAuditsAccess() {
        when(repository.findById(DOC_ID))
                .thenReturn(Optional.of(document(CALLER_TENANT, ClassificationLevel.CONFIDENTIAL)));

        handler.handle(query("NOTARIS"));

        AuditEventPayload event = singlePublishedEvent();
        assertThat(event.eventType()).isEqualTo("DOCUMENT_ACCESS");
        assertThat(event.outcome()).isEqualTo("SUCCESS");
    }
}
