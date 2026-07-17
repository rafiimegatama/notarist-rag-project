package com.notarist.verification.domain.valueobject;

/**
 * How a checklist item is evaluated.
 *
 * <ul>
 *   <li>{@code AUTOMATIC} — computed from OCR-review output (authority/director/NPWP-NIK/certificate
 *       mismatch, SKMHT deadline, duplicate certificate number, document consistency). Never an LLM.</li>
 *   <li>{@code MANUAL}    — a human observation (signature/seal authenticity, physical condition).</li>
 * </ul>
 */
public enum CheckType {
    AUTOMATIC,
    MANUAL
}
