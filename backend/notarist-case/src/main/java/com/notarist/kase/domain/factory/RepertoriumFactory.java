package com.notarist.kase.domain.factory;

import com.notarist.kase.domain.model.Repertorium;
import com.notarist.kase.domain.valueobject.RepertoriumId;

import java.time.LocalDate;
import java.util.UUID;

/** One register per notary, per year. */
public final class RepertoriumFactory {

    private RepertoriumFactory() {}

    public static Repertorium createFor(UUID notarisId, UUID tenantId, int year) {
        return Repertorium.forYear(RepertoriumId.generate(), notarisId, tenantId, year);
    }

    public static Repertorium createForCurrentYear(UUID notarisId, UUID tenantId) {
        return createFor(notarisId, tenantId, LocalDate.now().getYear());
    }
}
