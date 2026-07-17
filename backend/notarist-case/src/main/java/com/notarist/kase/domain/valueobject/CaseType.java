package com.notarist.kase.domain.valueobject;

/**
 * The kind of notarial work. Mirrors the existing core {@code JenisAkta} vocabulary rather than
 * inventing a parallel one — the office already classifies work this way.
 *
 * <p>{@code requiresPpat} is load-bearing: PPAT deeds (APHT, SKMHT, AJB) are a distinct statutory
 * function, so the approval authority for them is a question for a lawyer, not an engineer. Until
 * that is answered, the Approval aggregate treats a notary as authorised for everything and this flag
 * simply records the distinction rather than acting on it.
 */
public enum CaseType {
    APHT(true),
    SKMHT(true),
    AJB(true),
    FIDUSIA(false),
    ROYA(false),
    WASIAT(false),
    KUASA(false),
    PENDIRIAN_PT(false),
    LAINNYA(false);

    private final boolean requiresPpat;

    CaseType(boolean requiresPpat) {
        this.requiresPpat = requiresPpat;
    }

    public boolean requiresPpat() {
        return requiresPpat;
    }
}
