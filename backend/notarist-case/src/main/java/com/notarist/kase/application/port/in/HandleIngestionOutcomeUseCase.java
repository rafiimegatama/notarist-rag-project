package com.notarist.kase.application.port.in;

/**
 * The Case subscribes to the ingestion pipeline through this.
 *
 * <p>The Case OBSERVES ingestion; it never drives it. {@code OCR_RUNNING} is a derived summary — "at
 * least one document in this case has not finished" — so the only thing the pipeline ever does to a
 * Case is tell it that a document is done. The Case then decides for itself whether that means the
 * whole bundle is finished.
 */
public interface HandleIngestionOutcomeUseCase {

    void handle(DocumentIngestionOutcome outcome);
}
