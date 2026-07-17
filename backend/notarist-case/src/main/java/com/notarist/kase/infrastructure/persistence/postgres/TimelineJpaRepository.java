package com.notarist.kase.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimelineJpaRepository extends JpaRepository<TimelineJpaEntity, String> {

    Optional<TimelineJpaEntity> findByCaseId(String caseId);
}
