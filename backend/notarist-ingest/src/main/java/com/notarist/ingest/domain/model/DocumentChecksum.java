package com.notarist.ingest.domain.model;

import java.util.regex.Pattern;

/** SHA-256 checksum value object — used for duplicate detection and integrity verification. */
public record DocumentChecksum(String sha256Hex) {

    private static final Pattern HEX_64 = Pattern.compile("^[a-fA-F0-9]{64}$");

    public DocumentChecksum {
        if (sha256Hex == null || !HEX_64.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException(
                "DocumentChecksum must be a 64-character hex string (SHA-256)");
        }
        sha256Hex = sha256Hex.toLowerCase();
    }

    public static DocumentChecksum of(String sha256Hex) {
        return new DocumentChecksum(sha256Hex);
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}
