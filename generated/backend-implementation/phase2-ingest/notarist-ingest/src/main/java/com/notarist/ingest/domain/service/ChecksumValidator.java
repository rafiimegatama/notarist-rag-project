package com.notarist.ingest.domain.service;

import com.notarist.ingest.domain.model.DocumentChecksum;
import com.notarist.ingest.domain.exception.IngestionStageException;
import com.notarist.ingest.domain.model.PipelineStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Domain service for SHA-256 checksum computation and validation. */
public final class ChecksumValidator {

    private ChecksumValidator() {}

    public static String compute(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String compute(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            DigestInputStream dis = new DigestInputStream(inputStream, digest);
            while (dis.read(buffer) != -1) { /* consume */ }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void assertMatch(DocumentChecksum expected, String actual, PipelineStatus failedAt) {
        if (!expected.sha256Hex().equalsIgnoreCase(actual)) {
            throw new IngestionStageException(
                    "INGEST_CHECKSUM_MISMATCH", failedAt, false,
                    "Checksum mismatch — expected: " + expected.sha256Hex() +
                    ", actual: " + actual);
        }
    }

    public static boolean matches(DocumentChecksum expected, String actual) {
        return expected.sha256Hex().equalsIgnoreCase(actual);
    }
}
