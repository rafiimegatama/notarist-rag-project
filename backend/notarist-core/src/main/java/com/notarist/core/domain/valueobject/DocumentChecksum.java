package com.notarist.core.domain.valueobject;

/** SHA-256 hex checksum for a document binary — used for duplicate detection and corruption guards. */
public record DocumentChecksum(String sha256Hex) {

    public DocumentChecksum {
        if (sha256Hex == null || sha256Hex.isBlank())
            throw new IllegalArgumentException("DocumentChecksum sha256Hex must not be blank");
        if (sha256Hex.length() != 64)
            throw new IllegalArgumentException("DocumentChecksum sha256Hex must be 64 hex characters");
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}
