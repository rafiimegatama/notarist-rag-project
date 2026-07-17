package com.notarist.verification.domain.service;

import com.notarist.verification.domain.state.Decision;
import com.notarist.verification.domain.valueobject.ChecklistCategory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the automatic verification checks from OCR-review output. Pure and deterministic — it never
 * calls an LLM and never touches I/O; it takes {@link VerificationFacts} and returns one
 * {@link AutomaticCheckResult} per check.
 *
 * <p>Convention: a fact that was not extracted (null/empty) yields {@code MANUAL_REQUIRED} — the check
 * cannot be decided automatically, so it must not silently pass. A real discrepancy yields
 * {@code FAIL} with a reason.
 */
public final class AutomaticCheckEvaluator {

    /** SKMHT deadlines inside this window are flagged for a human rather than auto-passed. */
    public static final Duration SKMHT_WARNING_WINDOW = Duration.ofDays(7);

    public List<AutomaticCheckResult> evaluateAll(VerificationFacts f) {
        List<AutomaticCheckResult> results = new ArrayList<>();
        results.add(authorityMismatch(f));
        results.add(directorMismatch(f));
        results.add(npwpNikMismatch(f));
        results.add(certificateMismatch(f));
        results.add(duplicateCertificateNumber(f));
        results.add(skmhtDeadline(f));
        results.add(documentConsistency(f));
        return results;
    }

    // ---- individual checks ---------------------------------------------------------------------

    public AutomaticCheckResult authorityMismatch(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.AUTHORITY;
        String title = "Authority clause matches extracted authority";
        if (isBlank(f.authorityDeclared()) || isBlank(f.authorityExtracted())) {
            return manual(cat, title, "Authority not fully extracted — needs manual check");
        }
        return normEq(f.authorityDeclared(), f.authorityExtracted())
                ? pass(cat, title)
                : fail(cat, title, "Authority mismatch: declared '" + f.authorityDeclared()
                        + "', extracted '" + f.authorityExtracted() + "'");
    }

    public AutomaticCheckResult directorMismatch(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.DIRECTOR;
        String title = "Current directors match extracted directors";
        if (f.directorsDeclared().isEmpty() || f.directorsExtracted().isEmpty()) {
            return manual(cat, title, "Director list not fully extracted — needs manual check");
        }
        Set<String> declared = normSet(f.directorsDeclared());
        Set<String> extracted = normSet(f.directorsExtracted());
        return declared.equals(extracted)
                ? pass(cat, title)
                : fail(cat, title, "Director mismatch: declared " + f.directorsDeclared()
                        + ", extracted " + f.directorsExtracted());
    }

    public AutomaticCheckResult npwpNikMismatch(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.NPWP_NIK;
        String title = "NPWP/NIK valid and consistent";
        if (isBlank(f.npwp()) && isBlank(f.nik())) {
            return manual(cat, title, "NPWP/NIK not extracted — needs manual check");
        }
        List<String> problems = new ArrayList<>();
        if (!isBlank(f.npwp()) && !isValidNpwp(f.npwp())) {
            problems.add("NPWP '" + f.npwp() + "' is not 15–16 digits");
        }
        if (!isBlank(f.nik()) && !isValidNik(f.nik())) {
            problems.add("NIK '" + f.nik() + "' is not 16 digits");
        }
        return problems.isEmpty() ? pass(cat, title) : fail(cat, title, String.join("; ", problems));
    }

    public AutomaticCheckResult certificateMismatch(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.CERTIFICATE;
        String title = "Certificate number matches the deed";
        if (isBlank(f.certificateNumberExtracted()) || isBlank(f.certificateNumberExpected())) {
            return manual(cat, title, "Certificate number not fully extracted — needs manual check");
        }
        return normEq(f.certificateNumberExtracted(), f.certificateNumberExpected())
                ? pass(cat, title)
                : fail(cat, title, "Certificate mismatch: extracted '" + f.certificateNumberExtracted()
                        + "', expected '" + f.certificateNumberExpected() + "'");
    }

    public AutomaticCheckResult duplicateCertificateNumber(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.CERTIFICATE;
        String title = "No duplicate certificate number in the bundle";
        if (f.certificateNumbers().isEmpty()) {
            return notApplicable(cat, title, "No certificate numbers to check");
        }
        Set<String> seen = new LinkedHashSet<>();
        Set<String> dupes = new LinkedHashSet<>();
        for (String number : f.certificateNumbers()) {
            String n = norm(number);
            if (!seen.add(n)) dupes.add(number);
        }
        return dupes.isEmpty() ? pass(cat, title)
                : fail(cat, title, "Duplicate certificate number(s): " + dupes);
    }

    public AutomaticCheckResult skmhtDeadline(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.SKMHT_DEADLINE;
        String title = "SKMHT deadline is not overdue";
        if (f.skmhtDeadline() == null) {
            return notApplicable(cat, title, "Bundle has no SKMHT deadline");
        }
        Instant now = f.evaluatedAt();
        if (f.skmhtDeadline().isBefore(now)) {
            return fail(cat, title, "SKMHT deadline " + f.skmhtDeadline() + " has passed");
        }
        if (f.skmhtDeadline().isBefore(now.plus(SKMHT_WARNING_WINDOW))) {
            return manual(cat, title, "SKMHT deadline " + f.skmhtDeadline() + " is within "
                    + SKMHT_WARNING_WINDOW.toDays() + " days — confirm follow-up");
        }
        return pass(cat, title);
    }

    public AutomaticCheckResult documentConsistency(VerificationFacts f) {
        ChecklistCategory cat = ChecklistCategory.DOCUMENT_CONSISTENCY;
        String title = "Required supporting documents are present";
        if (f.requiredDocumentTypes().isEmpty()) {
            return notApplicable(cat, title, "No required document set defined");
        }
        Set<String> present = normSet(f.presentDocumentTypes());
        List<String> missing = f.requiredDocumentTypes().stream()
                .filter(req -> !present.contains(norm(req)))
                .collect(Collectors.toList());
        return missing.isEmpty() ? pass(cat, title)
                : fail(cat, title, "Missing required document(s): " + missing);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static boolean isValidNpwp(String npwp) {
        String digits = npwp.replaceAll("\\D", "");
        return digits.length() == 15 || digits.length() == 16;
    }

    private static boolean isValidNik(String nik) {
        String digits = nik.replaceAll("\\D", "");
        return digits.length() == 16;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static boolean normEq(String a, String b) { return norm(a).equals(norm(b)); }

    private static Set<String> normSet(List<String> values) {
        return values.stream().map(AutomaticCheckEvaluator::norm).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static AutomaticCheckResult pass(ChecklistCategory c, String t) {
        return new AutomaticCheckResult(c, t, true, Decision.PASS, null);
    }
    private static AutomaticCheckResult fail(ChecklistCategory c, String t, String reason) {
        return new AutomaticCheckResult(c, t, true, Decision.FAIL, reason);
    }
    private static AutomaticCheckResult manual(ChecklistCategory c, String t, String reason) {
        return new AutomaticCheckResult(c, t, true, Decision.MANUAL_REQUIRED, reason);
    }
    private static AutomaticCheckResult notApplicable(ChecklistCategory c, String t, String reason) {
        return new AutomaticCheckResult(c, t, true, Decision.NOT_APPLICABLE, reason);
    }
}
