package com.notarist.auth.infrastructure.persistence.postgres;

import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.domain.model.User;
import com.notarist.auth.infrastructure.persistence.mapper.UserEntityMapper;
import com.notarist.auth.infrastructure.security.RlsContextApplier;
import com.notarist.core.domain.valueobject.PersonId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The tenant identity is applied at the start of each method; @Transactional guarantees the
 * notarist_set_identity() call and the subsequent JPA query share one PostgreSQL connection and
 * transaction, which is what makes the transaction-local setting visible to the row-level-security
 * policy. PostgreSQL discards the setting at commit/rollback, so it cannot leak to the next
 * borrower of the pooled connection.
 */
@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserEntityMapper mapper;
    private final RlsContextApplier rlsContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public UserRepositoryImpl(
            UserJpaRepository jpaRepository,
            UserEntityMapper mapper,
            RlsContextApplier rlsContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.rlsContextApplier = rlsContextApplier;
    }

    /**
     * Pre-authentication login lookup. There is no principal yet — this query reads the very row
     * that reveals the caller's tenant — so it runs as a trusted system session, exempt from the
     * fail-closed RLS tenant policy (Flyway V9). Without the exemption the row is invisible
     * and no one can ever log in.
     *
     * <p>Prefer {@link #findByUsernameAndTenantId} anywhere the tenant is already known: that one
     * runs under the caller's own identity and IS tenant-filtered by the database.
     */
    @Override
    public Optional<User> findByUsername(String username) {
        rlsContextApplier.applySystemIdentity(entityManager);
        return jpaRepository.findByUsername(username).map(mapper::toDomain);
    }

    /**
     * Looks a user up under the tenant identity of an already-validated session, for flows that
     * have no authenticated principal (refresh-token rotation on the permitAll /auth/refresh
     * endpoint). The VPD policy still filters the row by {@code tenantId}: a session whose tenant
     * does not own the user sees nothing, so this cannot be used to read across tenants.
     */
    @Override
    public Optional<User> findByIdAndTenantId(PersonId userId, UUID tenantId) {
        rlsContextApplier.applyIdentity(
                entityManager, userId.value().toString(), tenantId.toString(), null);
        return jpaRepository.findById(userId.value().toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsernameAndTenantId(String username, UUID tenantId) {
        rlsContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByUsernameAndTenantId(username, tenantId.toString())
                .map(mapper::toDomain);
    }
}
