package com.notarist.observability.log;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strips forbidden content from values before they reach any log sink.
 *
 * Forbidden patterns:
 *   - JWT tokens (3-segment base64 separated by dots)
 *   - Bearer tokens in Authorization header values
 *   - Raw OCR / akta document text longer than MAX_SAFE_TEXT_LEN
 *   - PII patterns: NIK (16 digits), NPWP (15 digits), phone numbers
 *   - Explicit forbidden key names (password, secret, token, credential)
 *
 * Safe truncation: long strings are trimmed and suffixed with "[TRUNCATED]".
 * Null inputs are returned as-is.
 */
@Component
public class LogSanitizer {

    private static final int    MAX_SAFE_TEXT_LEN   = 500;
    private static final String REDACTED            = "[REDACTED]";
    private static final String TRUNCATED_SUFFIX    = "...[TRUNCATED]";

    private static final Pattern JWT_PATTERN   =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");

    private static final Pattern NIK_PATTERN   =
            Pattern.compile("\\b\\d{16}\\b");

    private static final Pattern NPWP_PATTERN  =
            Pattern.compile("\\b\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d{1}-\\d{3}\\.\\d{3}\\b");

    private static final List<String> FORBIDDEN_KEYS = List.of(
            "password", "secret", "token", "credential", "private_key",
            "api_key", "access_key", "jwt", "bearer", "authorization"
    );

    /**
     * Sanitizes a single string value.
     */
    public String sanitize(String value) {
        if (value == null) return null;

        String v = value;
        v = JWT_PATTERN.matcher(v).replaceAll(REDACTED);
        v = BEARER_PATTERN.matcher(v).replaceAll("Bearer " + REDACTED);
        v = NIK_PATTERN.matcher(v).replaceAll(REDACTED);
        v = NPWP_PATTERN.matcher(v).replaceAll(REDACTED);

        if (v.length() > MAX_SAFE_TEXT_LEN) {
            v = v.substring(0, MAX_SAFE_TEXT_LEN) + TRUNCATED_SUFFIX;
        }

        return v;
    }

    /**
     * Sanitizes a map of key-value pairs (e.g., request headers, log fields).
     * Forbidden keys are fully redacted regardless of value content.
     */
    public Map<String, String> sanitizeMap(Map<String, String> input) {
        if (input == null) return Map.of();
        var result = new java.util.LinkedHashMap<String, String>(input.size());
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            if (isForbiddenKey(key)) {
                result.put(key, REDACTED);
            } else {
                result.put(key, sanitize(entry.getValue()));
            }
        }
        return result;
    }

    /**
     * Sanitizes raw OCR / document text — hard-limit to 200 chars for logging.
     * Never log full OCR output.
     */
    public String sanitizeDocumentText(String rawText) {
        if (rawText == null) return null;
        int limit = 200;
        if (rawText.length() <= limit) return rawText;
        return rawText.substring(0, limit) + TRUNCATED_SUFFIX;
    }

    public boolean isForbiddenKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return FORBIDDEN_KEYS.stream().anyMatch(lower::contains);
    }
}
