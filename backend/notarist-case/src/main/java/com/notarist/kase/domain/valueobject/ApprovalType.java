package com.notarist.kase.domain.valueobject;



import java.util.Set;

/**
 * What is being approved — and therefore who is allowed to approve it.
 *
 * <p>The authority table lives on the type itself rather than in a service, so the rule travels with
 * the concept and cannot be forgotten by a new caller.
 */
public enum ApprovalType {

    /**
     * Sign-off that the draft passed quality control. Requires four eyes: the person who produced the
     * work may not also certify it.
     */
    QC_SIGNOFF(
            Set.of(Role.NOTARIS, Role.PPAT_OFFICER, Role.PIMPINAN),
            true),

    /**
     * The notary's signature. The most restricted operation in the system.
     *
     * <p>ADMIN is absent, and so is PIMPINAN — deliberately. Notarial authority is statutory and
     * personal; it is not an org-chart permission and cannot be escalated upward. PPAT_OFFICER is
     * included because PPAT is a distinct statutory office with its own signing authority for
     * APHT/SKMHT/AJB deeds. (Whether a PPAT officer may sign a *non*-PPAT deed is a question for a
     * lawyer; today the type does not distinguish, and that is recorded as an open decision.)
     */
    NOTARY_SIGNATURE(
            Set.of(Role.NOTARIS, Role.PPAT_OFFICER),
            true);

    private final Set<Role> allowedRoles;
    private final boolean requiresFourEyes;

    ApprovalType(Set<Role> allowedRoles, boolean requiresFourEyes) {
        this.allowedRoles = allowedRoles;
        this.requiresFourEyes = requiresFourEyes;
    }

    public Set<Role> allowedRoles() {
        return allowedRoles;
    }

    public boolean mayBeDecidedBy(Role role) {
        return allowedRoles.contains(role);
    }

    public boolean requiresFourEyes() {
        return requiresFourEyes;
    }
}
