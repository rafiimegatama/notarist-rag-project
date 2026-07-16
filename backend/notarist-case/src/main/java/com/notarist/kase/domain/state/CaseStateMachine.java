package com.notarist.kase.domain.state;

import com.notarist.kase.domain.valueobject.Role;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.notarist.kase.domain.state.CaseState.*;

/**
 * The authoritative Case transition table. Nothing that is not listed here is possible.
 *
 * <p>Used only from inside the {@code Case} aggregate — it is package-visible behaviour, not a helper
 * that a service may call to "check first and then mutate". The aggregate has no public state setter,
 * so this table is the single gate through which state changes pass.
 *
 * <p>Each edge carries three things the domain actually needs: whether it is legal, what <em>kind</em>
 * of move it is (forward / retry / rollback / cancel), and which roles may perform it.
 */
public final class CaseStateMachine {

    private CaseStateMachine() {}

    /** A legal edge: its kind, and who may take it. */
    public record Edge(TransitionKind kind, Set<Role> allowedRoles) {

        static Edge forward(Role... roles) { return new Edge(TransitionKind.FORWARD, Set.of(roles)); }
        static Edge retry(Role... roles)   { return new Edge(TransitionKind.RETRY, Set.of(roles)); }
        static Edge rollback(Role... roles){ return new Edge(TransitionKind.ROLLBACK, Set.of(roles)); }
        static Edge cancel(Role... roles)  { return new Edge(TransitionKind.CANCEL, Set.of(roles)); }

        public boolean permits(Role role) {
            return allowedRoles.contains(role);
        }
    }

    private static final Role[] STAFF_AND_UP =
            { Role.STAFF, Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN, Role.ADMIN };

    private static final Map<CaseState, Map<CaseState, Edge>> TABLE = build();

    private static Map<CaseState, Map<CaseState, Edge>> build() {
        Map<CaseState, Map<CaseState, Edge>> t = new EnumMap<>(CaseState.class);

        t.put(CASE_CREATED, Map.of(
                UPLOADING, Edge.forward(STAFF_AND_UP),
                CANCELLED, Edge.cancel(Role.STAFF, Role.NOTARIS, Role.PIMPINAN, Role.ADMIN)));

        t.put(UPLOADING, Map.of(
                OCR_RUNNING, Edge.forward(STAFF_AND_UP),
                CANCELLED,   Edge.cancel(Role.STAFF, Role.NOTARIS, Role.PIMPINAN, Role.ADMIN)));

        // Driven by the ingestion pipeline via domain events — hence SYSTEM.
        t.put(OCR_RUNNING, Map.of(
                FIELD_EXTRACTION, Edge.forward(Role.SYSTEM),
                OCR_FAILED,       Edge.forward(Role.SYSTEM)));

        t.put(OCR_FAILED, Map.of(
                OCR_RUNNING, Edge.retry(Role.STAFF, Role.ADMIN, Role.NOTARIS, Role.PIMPINAN),
                UPLOADING,   Edge.rollback(STAFF_AND_UP)));   // document must be re-scanned

        t.put(FIELD_EXTRACTION, Map.of(
                WAITING_VERIFICATION, Edge.forward(Role.SYSTEM)));

        // The liability boundary: a HUMAN accepts the extracted facts. Never SYSTEM.
        t.put(WAITING_VERIFICATION, Map.of(
                VERIFIED,  Edge.forward(Role.STAFF, Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN),
                UPLOADING, Edge.rollback(Role.STAFF, Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN),
                CANCELLED, Edge.cancel(Role.STAFF, Role.NOTARIS, Role.PIMPINAN, Role.ADMIN)));

        t.put(VERIFIED, Map.of(
                GENERATING_DRAFT, Edge.forward(STAFF_AND_UP)));

        t.put(GENERATING_DRAFT, Map.of(
                WAITING_QC,   Edge.forward(Role.SYSTEM),
                DRAFT_FAILED, Edge.forward(Role.SYSTEM)));

        t.put(DRAFT_FAILED, Map.of(
                GENERATING_DRAFT, Edge.retry(STAFF_AND_UP)));

        t.put(WAITING_QC, Map.of(
                QC_APPROVED, Edge.forward(Role.SYSTEM),
                QC_FAILED,   Edge.forward(Role.SYSTEM),
                CANCELLED,   Edge.cancel(Role.ADMIN, Role.NOTARIS, Role.PIMPINAN)));

        // The hinge of the whole workflow. QC failed — but WHY? If the draft was wrong, regenerate.
        // If the source facts were wrong, go back and re-verify. That distinction requires human
        // judgement, so the machine offers both edges and refuses to choose.
        t.put(QC_FAILED, Map.of(
                GENERATING_DRAFT,     Edge.rollback(STAFF_AND_UP),
                WAITING_VERIFICATION, Edge.rollback(STAFF_AND_UP)));

        t.put(QC_APPROVED, Map.of(
                WAITING_NOTARY_APPROVAL, Edge.forward(STAFF_AND_UP)));

        // Notarial authority is statutory and personal. NOT delegable upward: an ADMIN or PIMPINAN
        // who can sign a deed is a fraud vector, not a convenience.
        t.put(WAITING_NOTARY_APPROVAL, Map.of(
                FINALIZED, Edge.forward(Role.NOTARIS, Role.PPAT_OFFICER),
                QC_FAILED, Edge.rollback(Role.NOTARIS, Role.PPAT_OFFICER)));

        t.put(FINALIZED, Map.of(
                DELIVERED, Edge.forward(STAFF_AND_UP)));

        t.put(DELIVERED, Map.of(
                ARCHIVED, Edge.forward(Role.SYSTEM, Role.ADMIN, Role.NOTARIS, Role.PIMPINAN)));

        // Terminal. No outbound edges — deliberately absent, not empty by accident.
        t.put(ARCHIVED, Map.of());
        t.put(CANCELLED, Map.of());

        return t;
    }

    /** The edge from → to, if it is legal. */
    public static Optional<Edge> edge(CaseState from, CaseState to) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(to));
    }

    public static boolean isLegal(CaseState from, CaseState to) {
        return edge(from, to).isPresent();
    }

    /** Every state reachable from {@code from}. Used by the UI to offer only lawful actions. */
    public static Set<CaseState> allowedTargets(CaseState from) {
        return TABLE.getOrDefault(from, Map.of()).keySet();
    }
}
