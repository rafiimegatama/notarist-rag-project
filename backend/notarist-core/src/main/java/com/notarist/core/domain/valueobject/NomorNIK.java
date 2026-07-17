package com.notarist.core.domain.valueobject;

/**
 * Value object untuk NIK (Nomor Induk Kependudukan).
 * S1-classified: stored encrypted via AES-256 at the application layer (plus whatever encryption-at-rest the managed database provides).
 * NEVER log or expose the raw value — only expose masked form: ****-****-****-1234.
 */
public final class NomorNIK {

    private static final int NIK_LENGTH = 16;

    private final String encryptedValue;

    private NomorNIK(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    /** Create from already-encrypted storage value (as read from the database). */
    public static NomorNIK fromEncrypted(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted NIK must not be null or blank");
        }
        return new NomorNIK(encryptedValue);
    }

    /** Returns the encrypted value for persistence. Never expose raw NIK via this method. */
    public String getEncryptedValue() {
        return encryptedValue;
    }

    /** Safe masked representation for display: ****-****-****-XXXX */
    public String masked() {
        return "****-****-****-[REDACTED]";
    }

    @Override
    public String toString() {
        return "[NIK:REDACTED]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NomorNIK other)) return false;
        return encryptedValue.equals(other.encryptedValue);
    }

    @Override
    public int hashCode() {
        return encryptedValue.hashCode();
    }
}
