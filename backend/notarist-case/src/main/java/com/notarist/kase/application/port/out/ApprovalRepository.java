package com.notarist.kase.application.port.out;

import com.notarist.kase.domain.model.Approval;
import com.notarist.kase.domain.valueobject.ApprovalId;
import com.notarist.kase.domain.valueobject.CaseId;
import com.notarist.kase.domain.valueobject.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository {

    void save(Approval approval);

    Optional<Approval> findById(ApprovalId approvalId);

    List<Approval> findByCase(CaseId caseId);

    /** "What is waiting for my signature?" — the hot, cross-case query. */
    List<Approval> findPendingForRole(UUID tenantId, Role role);
}
