package com.notarist.kase.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Human-facing case reference, tenant-unique. Format {seq}/{roman-or-numeric month}/{year} —
 * intentionally the same shape as the existing core {@code NomorAkta}, because that is the format the
 * office already reads and writes.
 *
 * <p>Distinct from a nomor akta: a CaseNumber identifies the <em>work</em>, and exists from the moment
 * the case is opened. A nomor akta identifies the <em>deed</em>, and is allocated from the
 * Repertorium only at finalization.
 */
public record CaseNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("^\\d+/[IVXLCDM\\d]+/\\d{4}$");

    public CaseNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CaseNumber must not be blank");
        }
        if (!FORMAT.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "CaseNumber format tidak valid: '" + value + "'. Format: {nomor}/{bulan}/{tahun}");
        }
        value = value.trim();
    }

    public static CaseNumber of(String value) {
        return new CaseNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
