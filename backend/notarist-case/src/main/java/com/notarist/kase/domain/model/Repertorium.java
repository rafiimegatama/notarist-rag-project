package com.notarist.kase.domain.model;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.core.domain.valueobject.TraceId;
import com.notarist.kase.domain.event.RepertoriumNumberAllocated;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.RepertoriumEntryId;
import com.notarist.kase.domain.valueobject.RepertoriumId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for the notary's official register of deeds — one per notary, per year.
 *
 * <p>This is the most legally exacting object in the system, and the one most likely to be got wrong.
 * Three properties are statutory, not preferences:
 *
 * <ol>
 *   <li><b>Gapless.</b> Numbers run 1, 2, 3 … with no holes. A missing number is a regulatory finding.
 *       This is why a database {@code SEQUENCE} is <em>not</em> an acceptable implementation: sequences
 *       leave gaps on rollback, which is exactly what the law forbids. The next number is derived from
 *       the entries themselves, inside a serialized transaction.</li>
 *   <li><b>Append-only.</b> No deletion, no renumbering, no reuse. There is no method here that
 *       removes an entry, at any privilege level.</li>
 *   <li><b>Allocate-once per case.</b> {@link #allocate} is <b>idempotent on caseId</b>: asking twice
 *       returns the SAME number rather than burning a second one. This is what makes the
 *       "allocate, then transition" sequence safe — if the FINALIZED transition fails after allocation
 *       and the operation is retried, the case gets its original number back instead of a new one, and
 *       the burnt number does not become a gap.</li>
 * </ol>
 *
 * <p>⚠️ Allocation must never be wrapped in an automatic retry loop (see
 * {@link RepertoriumNumberAllocated}). A retry around statutory numbering is precisely how a duplicate
 * or a gap gets manufactured. A failure here pages a human.
 */
public class Repertorium extends AggregateRoot {

    private final RepertoriumId repertoriumId;
    private final UUID notarisId;
    private final UUID tenantId;
    private final int year;

    private final List<RepertoriumEntry> entries = new ArrayList<>();

    private Repertorium(RepertoriumId repertoriumId, UUID notarisId, UUID tenantId, int year) {
        this.repertoriumId = repertoriumId;
        this.notarisId = notarisId;
        this.tenantId = tenantId;
        this.year = year;
    }

    public static Repertorium forYear(RepertoriumId repertoriumId, UUID notarisId, UUID tenantId, int year) {
        if (notarisId == null) throw new InvariantViolationException("notarisId is required");
        if (tenantId == null)  throw new InvariantViolationException("tenantId is required");
        if (year < 2000 || year > 2999) {
            throw new InvariantViolationException("Implausible repertorium year: " + year);
        }
        Repertorium r = new Repertorium(repertoriumId, notarisId, tenantId, year);
        r.validate();
        return r;
    }

    public static Repertorium rehydrate(RepertoriumId repertoriumId, UUID notarisId, UUID tenantId,
                                        int year, List<RepertoriumEntry> entries) {
        Repertorium r = new Repertorium(repertoriumId, notarisId, tenantId, year);
        if (entries != null) r.entries.addAll(entries);
        r.validate();   // a rehydrated register with a gap is a defect we want to hear about at load
        return r;
    }

    // ---- Allocation ----------------------------------------------------------------------------

    /**
     * Allocates the next number for a case — or returns the one it already has.
     *
     * <p><b>Idempotent on caseId, deliberately.</b> Retrying an allocation must never mint a second
     * number for the same deed, and must never leave the first one stranded as a gap.
     */
    public NomorAkta allocate(CaseId caseId, CorrelationId correlationId, TraceId traceId) {
        if (caseId == null) throw new InvariantViolationException("caseId is required");

        Optional<RepertoriumEntry> existing = findByCase(caseId);
        if (existing.isPresent()) {
            return existing.get().nomorAkta();   // already allocated — same number, no new event
        }

        int sequence = entries.size() + 1;                 // gapless by construction
        NomorAkta nomorAkta = NomorAkta.of(sequence + "/" + romanMonth() + "/" + year);

        entries.add(new RepertoriumEntry(
                RepertoriumEntryId.generate(), caseId, nomorAkta, sequence, Instant.now()));

        enforceInvariants();

        raise(new RepertoriumNumberAllocated(repertoriumId, caseId, nomorAkta, sequence, year,
                notarisId, tenantId, correlationId, traceId));

        return nomorAkta;
    }

    /**
     * The month component of the nomor akta, in Roman numerals — the convention Indonesian notarial
     * practice uses ("45/V/2024").
     */
    private String romanMonth() {
        String[] roman = {"I", "II", "III", "IV", "V", "VI",
                          "VII", "VIII", "IX", "X", "XI", "XII"};
        int month = java.time.LocalDate.now().getMonthValue();
        return roman[month - 1];
    }

    // ---- Invariants ----------------------------------------------------------------------------

    @Override
    public void validate() {
        if (repertoriumId == null) throw new InvariantViolationException("repertoriumId is required");
        if (notarisId == null)     throw new InvariantViolationException("notarisId is required");
        if (tenantId == null)      throw new InvariantViolationException("tenantId is required");

        for (int i = 0; i < entries.size(); i++) {
            int expected = i + 1;
            if (entries.get(i).sequence() != expected) {
                throw new InvariantViolationException(
                        "Repertorium " + repertoriumId + " has a gap: expected sequence " + expected
                                + " but found " + entries.get(i).sequence()
                                + ". A gap in the register is a regulatory finding.");
            }
        }

        long distinctCases = entries.stream().map(RepertoriumEntry::caseId).distinct().count();
        if (distinctCases != entries.size()) {
            throw new InvariantViolationException(
                    "Repertorium " + repertoriumId + " allocated more than one number to a single case");
        }
    }

    // ---- Queries -------------------------------------------------------------------------------

    public Optional<RepertoriumEntry> findByCase(CaseId caseId) {
        return entries.stream().filter(e -> e.caseId().equals(caseId)).findFirst();
    }

    public int nextSequence()                    { return entries.size() + 1; }
    public RepertoriumId repertoriumId()         { return repertoriumId; }
    public UUID notarisId()                      { return notarisId; }
    public UUID tenantId()                       { return tenantId; }
    public int year()                            { return year; }
    public int entryCount()                      { return entries.size(); }
    public List<RepertoriumEntry> entries()      { return Collections.unmodifiableList(entries); }
}
