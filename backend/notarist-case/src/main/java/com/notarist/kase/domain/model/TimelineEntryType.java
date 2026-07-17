package com.notarist.kase.domain.model;

/** What kind of thing happened. Drives the icon and grouping the UI will eventually render. */
public enum TimelineEntryType {
    CASE_OPENED,
    STATE_CHANGED,
    DOCUMENT_ATTACHED,
    BUNDLE_LOCKED,
    VERIFICATION,
    DRAFT,
    QC,
    APPROVAL,
    ROLLBACK,
    DELIVERY,
    EXCEPTION,
    NOTE
}
