package com.notarist.verification.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationItemAuditJpaRepository
        extends JpaRepository<VerificationItemAuditJpaEntity, String> {

    List<VerificationItemAuditJpaEntity> findByVerificationIdOrderBySequenceAsc(String verificationId);
}
