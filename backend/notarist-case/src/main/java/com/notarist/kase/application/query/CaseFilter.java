package com.notarist.kase.application.query;

import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;

import java.time.Instant;
import java.util.UUID;

/**
 * Optional filters for the case list. Every field is nullable — a null drops that predicate, so an
 * all-null filter lists every case in the tenant.
 *
 * <p><b>What is deliberately absent:</b> {@code priority} and {@code bankPartner}. The requested
 * filter set named those, but the {@code Case} aggregate models neither field, and this sprint's rule
 * is to reuse the Domain Foundation without redesigning it — adding columns to the aggregate for the
 * sake of a filter would be exactly that. They are recorded as a known limitation in the report.
 * {@code assignedStaff} is served by {@code createdBy} (the staff member who opened the case), which
 * is the closest concept the aggregate actually carries.
 */
public record CaseFilter(
        CaseState state,
        CaseType caseType,
        UUID assignedNotarisId,
        UUID createdBy,
        Instant createdFrom,
        Instant createdTo
) {
    public static CaseFilter empty() {
        return new CaseFilter(null, null, null, null, null, null);
    }
}
