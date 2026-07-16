package com.notarist.kase.domain;

import com.notarist.core.domain.valueobject.DocumentId;
import com.notarist.core.domain.valueobject.NomorAkta;
import com.notarist.kase.domain.exception.AuthorityException;
import com.notarist.kase.domain.exception.IllegalTransitionException;
import com.notarist.kase.domain.exception.InvariantViolationException;
import com.notarist.kase.domain.factory.*;
import com.notarist.kase.domain.model.*;
import com.notarist.kase.domain.specification.ApprovalSpecifications;
import com.notarist.kase.domain.specification.BundleSpecifications;
import com.notarist.kase.domain.specification.CaseSpecifications;
import com.notarist.kase.domain.state.*;
import com.notarist.kase.domain.valueobject.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Bundle, Approval, Timeline and Repertorium: their invariants, and what they refuse to do. */
class AggregateInvariantsTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Actor STAFF = Actor.of(UUID.randomUUID(), Role.STAFF);
    private static final Actor NOTARIS = Actor.of(UUID.randomUUID(), Role.NOTARIS);
    private static final Actor PIMPINAN = Actor.of(UUID.randomUUID(), Role.PIMPINAN);
    private static final Actor ADMIN = Actor.of(UUID.randomUUID(), Role.ADMIN);

    private Case newCase() {
        return CaseFactory.create(CaseNumber.of("1/VII/2026"), CaseType.APHT, TENANT,
                STAFF, NOTARIS.userId(), null, null).aCase();
    }

    // ============================== BUNDLE ==============================

    @Nested
    @DisplayName("Bundle")
    class BundleTests {

        private Bundle bundleOf(int expected) {
            return BundleFactory.create(newCase(), BundleType.IDENTITY, expected, STAFF, null, null);
        }

        @Test
        @DisplayName("a bundle holds document IDs — never Document objects (the aggregate is reused, not owned)")
        void holdsIdsOnly() {
            Bundle b = bundleOf(2);
            DocumentId doc = DocumentId.generate();

            b.attachDocument(DocumentRef.of(doc, "KTP"), STAFF, null, null);

            assertThat(b.documents()).hasSize(1);
            assertThat(b.documents().get(0).documentId()).isEqualTo(doc);
            assertThat(b.containsDocument(doc)).isTrue();
        }

        @Test
        @DisplayName("attaching the same document twice is a no-op, not an error (at-least-once delivery)")
        void attachIsIdempotent() {
            Bundle b = bundleOf(2);
            DocumentId doc = DocumentId.generate();

            b.attachDocument(DocumentRef.of(doc, "KTP"), STAFF, null, null);
            b.attachDocument(DocumentRef.of(doc, "KTP"), STAFF, null, null);

            assertThat(b.documentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a bundle cannot be COMPLETE while documents are missing")
        void cannotCompleteWhileIncomplete() {
            Bundle b = bundleOf(3);
            b.attachDocument(DocumentRef.of(DocumentId.generate(), "KTP"), STAFF, null, null);

            assertThatThrownBy(() -> b.transition(BundleStatus.COMPLETE, STAFF))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("not COMPLETE");
        }

        @Test
        @DisplayName("detaching a document reopens a COMPLETE bundle — the state must not lie")
        void detachReopens() {
            Bundle b = bundleOf(1);
            DocumentId doc = DocumentId.generate();
            b.attachDocument(DocumentRef.of(doc, "KTP"), STAFF, null, null);
            b.transition(BundleStatus.COMPLETE, STAFF);

            b.detachDocument(doc, STAFF);

            assertThat(b.status()).isEqualTo(BundleStatus.OPEN);
        }

        @Test
        @DisplayName("LOCKED is irreversible — there is no unlock, at any privilege level")
        void lockedIsIrreversible() {
            Bundle b = bundleOf(1);
            b.attachDocument(DocumentRef.of(DocumentId.generate(), "KTP"), STAFF, null, null);
            b.transition(BundleStatus.COMPLETE, STAFF);
            b.lock(NOTARIS, null, null);

            assertThat(b.status()).isEqualTo(BundleStatus.LOCKED);

            // Not even ADMIN can reopen it. The notary signed on the basis of these exact documents.
            assertThatThrownBy(() -> b.transition(BundleStatus.OPEN, ADMIN))
                    .isInstanceOf(IllegalTransitionException.class);
            assertThatThrownBy(() -> b.detachDocument(DocumentId.generate(), ADMIN))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("frozen");
            assertThatThrownBy(() ->
                    b.attachDocument(DocumentRef.of(DocumentId.generate(), "X"), ADMIN, null, null))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("an incomplete bundle cannot be locked")
        void cannotLockIncomplete() {
            Bundle b = bundleOf(2);
            b.attachDocument(DocumentRef.of(DocumentId.generate(), "KTP"), STAFF, null, null);

            assertThatThrownBy(() -> b.lock(NOTARIS, null, null))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("specifications describe the same rules the aggregate enforces")
        void specifications() {
            Bundle b = bundleOf(1);
            assertThat(BundleSpecifications.isComplete().isSatisfiedBy(b)).isFalse();
            assertThat(BundleSpecifications.acceptsDocuments().isSatisfiedBy(b)).isTrue();

            b.attachDocument(DocumentRef.of(DocumentId.generate(), "KTP"), STAFF, null, null);
            b.transition(BundleStatus.COMPLETE, STAFF);

            assertThat(BundleSpecifications.canBeLocked().isSatisfiedBy(b)).isTrue();
        }
    }

    // ============================== APPROVAL ==============================

    @Nested
    @DisplayName("Approval")
    class ApprovalTests {

        private Approval notarySignature(UUID submittedBy) {
            return ApprovalFactory.request(newCase(), ApprovalType.NOTARY_SIGNATURE, submittedBy, null, null);
        }

        @Test
        @DisplayName("only a NOTARIS may grant a notary signature — not ADMIN, not PIMPINAN")
        void onlyNotaryMaySign() {
            assertThatThrownBy(() -> notarySignature(STAFF.userId()).approve(ADMIN, null, null))
                    .isInstanceOf(AuthorityException.class)
                    .hasMessageContaining("statutory and personal");

            assertThatThrownBy(() -> notarySignature(STAFF.userId()).approve(PIMPINAN, null, null))
                    .isInstanceOf(AuthorityException.class);

            assertThatCode(() -> notarySignature(STAFF.userId()).approve(NOTARIS, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the system can never approve — it cannot assume legal responsibility")
        void systemCannotApprove() {
            assertThatThrownBy(() -> notarySignature(STAFF.userId()).approve(Actor.system(), null, null))
                    .isInstanceOf(AuthorityException.class);
        }

        @Test
        @DisplayName("four-eyes: you may not approve your own work")
        void fourEyes() {
            Approval a = notarySignature(NOTARIS.userId());   // the notary submitted it

            assertThatThrownBy(() -> a.approve(NOTARIS, null, null))
                    .isInstanceOf(AuthorityException.class)
                    .hasMessageContaining("Four-eyes");
        }

        @Test
        @DisplayName("a rejection without a reason is refused — the reason is a legally significant fact")
        void rejectionRequiresReason() {
            Approval a = notarySignature(STAFF.userId());

            assertThatThrownBy(() -> a.reject(NOTARIS, "  ", null, null))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("requires a reason");
        }

        @Test
        @DisplayName("a decision is never reversed — raise a new approval instead")
        void decisionIsFinal() {
            Approval a = notarySignature(STAFF.userId());
            a.approve(NOTARIS, null, null);

            assertThatThrownBy(() -> a.reject(NOTARIS, "berubah pikiran", null, null))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("never reversed");
        }

        @Test
        @DisplayName("approval raises ApprovalRequested then ApprovalGranted")
        void events() {
            Approval a = notarySignature(STAFF.userId());
            assertThat(a.domainEvents()).hasSize(1);

            a.approve(NOTARIS, null, null);
            assertThat(a.domainEvents()).hasSize(2);
            assertThat(a.domainEvents().get(1).eventType()).isEqualTo("ApprovalGranted");
        }

        @Test
        @DisplayName("decidableBy specification agrees with the aggregate's authority rules")
        void specification() {
            Approval a = notarySignature(STAFF.userId());

            assertThat(ApprovalSpecifications.decidableBy(NOTARIS).isSatisfiedBy(a)).isTrue();
            assertThat(ApprovalSpecifications.decidableBy(ADMIN).isSatisfiedBy(a)).isFalse();
            assertThat(ApprovalSpecifications.decidableBy(Actor.system()).isSatisfiedBy(a)).isFalse();

            Approval own = notarySignature(NOTARIS.userId());
            assertThat(ApprovalSpecifications.decidableBy(NOTARIS).isSatisfiedBy(own))
                    .as("four-eyes: cannot approve own work")
                    .isFalse();
        }
    }

    // ============================== TIMELINE ==============================

    @Nested
    @DisplayName("Timeline")
    class TimelineTests {

        @Test
        @DisplayName("the factory creates a case together with its timeline — no case without a history")
        void caseFactoryCreatesTimeline() {
            CaseFactory.NewCase created = CaseFactory.create(
                    CaseNumber.of("3/VII/2026"), CaseType.FIDUSIA, TENANT, STAFF, null, null, null);

            assertThat(created.timeline().entryCount()).isEqualTo(1);
            assertThat(created.timeline().entries().get(0).type()).isEqualTo(TimelineEntryType.CASE_OPENED);
        }

        @Test
        @DisplayName("entries are append-only and densely sequenced")
        void appendOnly() {
            Timeline t = TimelineFactory.createFor(CaseId.generate(), TENANT);

            t.append(TimelineEntryType.STATE_CHANGED, "UPLOADING", STAFF, null, null);
            t.append(TimelineEntryType.DOCUMENT_ATTACHED, "KTP", STAFF, null, null);

            assertThat(t.entryCount()).isEqualTo(2);
            assertThat(t.entries()).extracting(TimelineEntry::sequence).containsExactly(0, 1);
            // The list is a read-only view — there is no remove/update anywhere on this aggregate.
            assertThatThrownBy(() -> t.entries().remove(0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("a sealed timeline accepts no further entries — the story cannot grow a new chapter")
        void sealedRejectsEntries() {
            Timeline t = TimelineFactory.createFor(CaseId.generate(), TENANT);
            t.append(TimelineEntryType.NOTE, "catatan", STAFF, null, null);
            t.seal(null, null);

            assertThat(t.status()).isEqualTo(TimelineStatus.SEALED);
            assertThatThrownBy(() -> t.append(TimelineEntryType.NOTE, "lagi", STAFF, null, null))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("SEALED");
        }

        @Test
        @DisplayName("sealing is irreversible")
        void sealIsIrreversible() {
            Timeline t = TimelineFactory.createFor(CaseId.generate(), TENANT);
            t.seal(null, null);

            assertThatThrownBy(() -> t.transition(TimelineStatus.ACTIVE, null, null))
                    .isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("an entry must describe what happened")
        void entryNeedsDescription() {
            Timeline t = TimelineFactory.createFor(CaseId.generate(), TENANT);

            assertThatThrownBy(() -> t.append(TimelineEntryType.NOTE, "  ", STAFF, null, null))
                    .isInstanceOf(InvariantViolationException.class);
        }
    }

    // ============================== REPERTORIUM ==============================

    @Nested
    @DisplayName("Repertorium")
    class RepertoriumTests {

        private Repertorium register() {
            return RepertoriumFactory.createFor(NOTARIS.userId(), TENANT, 2026);
        }

        @Test
        @DisplayName("numbers are gapless: 1, 2, 3 …")
        void gapless() {
            Repertorium r = register();

            NomorAkta first = r.allocate(CaseId.generate(), null, null);
            NomorAkta second = r.allocate(CaseId.generate(), null, null);
            NomorAkta third = r.allocate(CaseId.generate(), null, null);

            assertThat(r.entryCount()).isEqualTo(3);
            assertThat(r.entries()).extracting(RepertoriumEntry::sequence).containsExactly(1, 2, 3);
            assertThat(first.value()).startsWith("1/");
            assertThat(second.value()).startsWith("2/");
            assertThat(third.value()).startsWith("3/");
        }

        @Test
        @DisplayName("allocation is IDEMPOTENT per case — retrying returns the SAME number, never burns one")
        void allocationIsIdempotent() {
            // This is what makes "allocate, then transition to FINALIZED" safe. If the transition fails
            // and the operation is retried, the case gets its original number back — the first is not
            // stranded as a gap, and no second number is minted for the same deed.
            Repertorium r = register();
            CaseId caseId = CaseId.generate();

            NomorAkta first = r.allocate(caseId, null, null);
            NomorAkta again = r.allocate(caseId, null, null);

            assertThat(again).isEqualTo(first);
            assertThat(r.entryCount()).as("no second number burned").isEqualTo(1);
            assertThat(r.domainEvents()).as("no second event raised").hasSize(1);
        }

        @Test
        @DisplayName("one case can never hold two numbers")
        void oneNumberPerCase() {
            Repertorium r = register();
            CaseId a = CaseId.generate();
            CaseId b = CaseId.generate();

            r.allocate(a, null, null);
            r.allocate(b, null, null);
            r.allocate(a, null, null);

            assertThat(r.entryCount()).isEqualTo(2);
            assertThat(r.findByCase(a)).isPresent();
            assertThatCode(r::validate).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("there is no operation that removes or renumbers an entry — a gap is a regulatory finding")
        void noRemoval() {
            Repertorium r = register();
            r.allocate(CaseId.generate(), null, null);

            assertThatThrownBy(() -> r.entries().remove(0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("rehydrating a register with a gap fails loudly at load")
        void rehydrationDetectsGap() {
            java.util.List<RepertoriumEntry> gapped = java.util.List.of(
                    new RepertoriumEntry(RepertoriumEntryId.generate(), CaseId.generate(),
                            NomorAkta.of("1/V/2026"), 1, java.time.Instant.now()),
                    new RepertoriumEntry(RepertoriumEntryId.generate(), CaseId.generate(),
                            NomorAkta.of("3/V/2026"), 3, java.time.Instant.now()));   // 2 is missing

            assertThatThrownBy(() -> Repertorium.rehydrate(
                    RepertoriumId.generate(), NOTARIS.userId(), TENANT, 2026, gapped))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessageContaining("gap");
        }

        @Test
        @DisplayName("allocation raises exactly one RepertoriumNumberAllocated")
        void raisesEvent() {
            Repertorium r = register();
            r.allocate(CaseId.generate(), null, null);

            assertThat(r.domainEvents()).hasSize(1);
            assertThat(r.domainEvents().get(0).eventType()).isEqualTo("RepertoriumNumberAllocated");
            assertThat(r.domainEvents().get(0).publishedBy()).isEqualTo("notarist-case");
        }
    }

    // ============================== SPECIFICATIONS ==============================

    @Nested
    @DisplayName("Case specifications")
    class CaseSpecs {

        @Test
        @DisplayName("specifications explain WHY they are unsatisfied, not just that they are")
        void reasonsAreExplained() {
            Case c = newCase();

            assertThat(CaseSpecifications.isActive().isSatisfiedBy(c)).isTrue();
            assertThat(CaseSpecifications.canBeFinalized().isSatisfiedBy(c)).isFalse();
            assertThat(CaseSpecifications.canBeFinalized().reasonUnsatisfied())
                    .isEqualTo("the case is not awaiting notary approval");
        }

        @Test
        @DisplayName("specifications compose with and/or/not")
        void compose() {
            Case c = newCase();

            assertThat(CaseSpecifications.isActive()
                    .and(CaseSpecifications.canBeCancelled())
                    .isSatisfiedBy(c)).isTrue();

            assertThat(CaseSpecifications.isTerminal().not().isSatisfiedBy(c)).isTrue();
        }
    }
}
