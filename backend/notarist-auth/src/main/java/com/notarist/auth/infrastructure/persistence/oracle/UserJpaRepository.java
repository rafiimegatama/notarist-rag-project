package com.notarist.auth.infrastructure.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, String> {
    Optional<UserJpaEntity> findByUsername(String username);
    Optional<UserJpaEntity> findByUsernameAndTenantId(String username, String tenantId);
}
