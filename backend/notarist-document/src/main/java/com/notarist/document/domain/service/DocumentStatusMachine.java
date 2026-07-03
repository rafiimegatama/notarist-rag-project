package com.notarist.document.domain.service;

import com.notarist.document.domain.model.DocumentLegal;
import com.notarist.document.domain.model.DocumentStatus;

import java.time.Instant;
import java.util.Set;

/** Domain service enforcing valid DocumentLegal status transitions. */
public final class DocumentStatusMachine {

    private DocumentStatusMachine() {}

    private static final Set<DocumentStatus> TERMINAL_STATUSES =
            Set.of(DocumentStatus.INDEXED, DocumentStatus.DLQ);

    public static void transition(DocumentLegal document, DocumentStatus newStatus) {
        DocumentStatus current = document.getStatus();

        if (TERMINAL_STATUSES.contains(current)) {
            throw new IllegalStateException(
                    "Document " + document.getDocumentId().value() +
                    " is in terminal status " + current + " — cannot transition to " + newStatus);
        }

        if (!isValidTransition(current, newStatus)) {
            throw new IllegalStateException(
                    "Invalid transition: " + current + " → " + newStatus);
        }

        if (newStatus == DocumentStatus.INDEXED) {
            document.markIndexed(Instant.now());
        } else {
            document.transitionStatus(newStatus);
        }
    }

    private static boolean isValidTransition(DocumentStatus from, DocumentStatus to) {
        return switch (from) {
            case UPLOADED             -> to == DocumentStatus.OCR_QUEUE || to == DocumentStatus.FAILED;
            case OCR_QUEUE            -> to == DocumentStatus.OCR_PROCESSING || to == DocumentStatus.FAILED;
            case OCR_PROCESSING       -> to == DocumentStatus.NER_QUEUE || to == DocumentStatus.FAILED;
            case NER_QUEUE            -> to == DocumentStatus.NER_PROCESSING || to == DocumentStatus.FAILED;
            case NER_PROCESSING       -> to == DocumentStatus.CHUNKING_QUEUE || to == DocumentStatus.FAILED;
            case CHUNKING_QUEUE       -> to == DocumentStatus.CHUNKING_PROCESSING || to == DocumentStatus.FAILED;
            case CHUNKING_PROCESSING  -> to == DocumentStatus.EMBEDDING_QUEUE || to == DocumentStatus.FAILED;
            case EMBEDDING_QUEUE      -> to == DocumentStatus.EMBEDDING_PROCESSING || to == DocumentStatus.FAILED;
            case EMBEDDING_PROCESSING -> to == DocumentStatus.INDEXING_QUEUE || to == DocumentStatus.FAILED;
            case INDEXING_QUEUE       -> to == DocumentStatus.INDEXING_PROCESSING || to == DocumentStatus.FAILED;
            case INDEXING_PROCESSING  -> to == DocumentStatus.INDEXED || to == DocumentStatus.FAILED;
            case FAILED               -> to == DocumentStatus.OCR_QUEUE || to == DocumentStatus.DLQ;
            default                   -> false;
        };
    }
}
