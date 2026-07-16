package com.notarist.verification.domain.service;

import java.time.Instant;
import java.util.List;

/**
 * The extracted facts an automatic check consumes. These come from OCR-review OUTPUT (supplied by an
 * out-port adapter at the composition root) — never from an LLM. A {@code null} field means "not
 * extracted / not available", which the evaluator turns into {@code MANUAL_REQUIRED} rather than a
 * silent pass.
 */
public record VerificationFacts(
        String authorityDeclared,
        String authorityExtracted,
        List<String> directorsDeclared,
        List<String> directorsExtracted,
        String npwp,
        String nik,
        String certificateNumberExtracted,
        String certificateNumberExpected,
        List<String> certificateNumbers,
        Instant skmhtDeadline,
        List<String> requiredDocumentTypes,
        List<String> presentDocumentTypes,
        Instant evaluatedAt
) {
    public VerificationFacts {
        directorsDeclared = directorsDeclared == null ? List.of() : List.copyOf(directorsDeclared);
        directorsExtracted = directorsExtracted == null ? List.of() : List.copyOf(directorsExtracted);
        certificateNumbers = certificateNumbers == null ? List.of() : List.copyOf(certificateNumbers);
        requiredDocumentTypes = requiredDocumentTypes == null ? List.of() : List.copyOf(requiredDocumentTypes);
        presentDocumentTypes = presentDocumentTypes == null ? List.of() : List.copyOf(presentDocumentTypes);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — the fact set is wide and mostly optional, so a builder reads better than a ctor. */
    public static final class Builder {
        private String authorityDeclared;
        private String authorityExtracted;
        private List<String> directorsDeclared;
        private List<String> directorsExtracted;
        private String npwp;
        private String nik;
        private String certificateNumberExtracted;
        private String certificateNumberExpected;
        private List<String> certificateNumbers;
        private Instant skmhtDeadline;
        private List<String> requiredDocumentTypes;
        private List<String> presentDocumentTypes;
        private Instant evaluatedAt;

        public Builder authority(String declared, String extracted) {
            this.authorityDeclared = declared; this.authorityExtracted = extracted; return this;
        }
        public Builder directors(List<String> declared, List<String> extracted) {
            this.directorsDeclared = declared; this.directorsExtracted = extracted; return this;
        }
        public Builder npwp(String npwp) { this.npwp = npwp; return this; }
        public Builder nik(String nik) { this.nik = nik; return this; }
        public Builder certificate(String extracted, String expected) {
            this.certificateNumberExtracted = extracted; this.certificateNumberExpected = expected; return this;
        }
        public Builder certificateNumbers(List<String> numbers) { this.certificateNumbers = numbers; return this; }
        public Builder skmhtDeadline(Instant deadline) { this.skmhtDeadline = deadline; return this; }
        public Builder requiredDocuments(List<String> required) { this.requiredDocumentTypes = required; return this; }
        public Builder presentDocuments(List<String> present) { this.presentDocumentTypes = present; return this; }
        public Builder evaluatedAt(Instant at) { this.evaluatedAt = at; return this; }

        public VerificationFacts build() {
            return new VerificationFacts(authorityDeclared, authorityExtracted, directorsDeclared,
                    directorsExtracted, npwp, nik, certificateNumberExtracted, certificateNumberExpected,
                    certificateNumbers, skmhtDeadline, requiredDocumentTypes, presentDocumentTypes,
                    evaluatedAt);
        }
    }
}
