package com.notarist.core.domain.valueobject;

import java.util.regex.Pattern;

/**
 * Value object untuk nomor akta notaris.
 * Format: {nomor}/{bulan-romawi}/{tahun} atau {nomor}/{bulan}/{tahun}
 * Contoh: "45/V/2024", "12/03/2025"
 */
public record NomorAkta(String value) {

    private static final Pattern FORMAT_PATTERN =
            Pattern.compile("^\\d+/[IVXLCDM\\d]+/\\d{4}$");

    public NomorAkta {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NomorAkta must not be null or blank");
        }
        if (!FORMAT_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                "NomorAkta format tidak valid: '" + value + "'. Format: {nomor}/{bulan}/{tahun}");
        }
    }

    public static NomorAkta of(String value) {
        return new NomorAkta(value.trim());
    }

    @Override
    public String toString() {
        return value;
    }
}
