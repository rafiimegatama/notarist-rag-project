package com.notarist.core.domain.valueobject;

/**
 * Value object untuk NPWP (Nomor Pokok Wajib Pajak).
 * S1-classified: stored encrypted via Oracle TDE + AES-256 app-level.
 * NEVER log or expose the raw value.
 */
public final class NomorNPWP {

    private final String encryptedValue;

    private NomorNPWP(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public static NomorNPWP fromEncrypted(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted NPWP must not be null or blank");
        }
        return new NomorNPWP(encryptedValue);
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public String masked() {
        return "**.**.**.**-***.***.***";
    }

    @Override
    public String toString() {
        return "[NPWP:REDACTED]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NomorNPWP other)) return false;
        return encryptedValue.equals(other.encryptedValue);
    }

    @Override
    public int hashCode() {
        return encryptedValue.hashCode();
    }
}
