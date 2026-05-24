package com.notarist.auth.infrastructure.persistence.oracle;

import com.notarist.auth.application.port.out.UserRepository;
import com.notarist.auth.domain.model.User;
import com.notarist.auth.infrastructure.persistence.mapper.UserEntityMapper;
import com.notarist.auth.infrastructure.security.VpdContextApplier;
import com.notarist.core.domain.valueobject.PersonId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserEntityMapper mapper;
    private final VpdContextApplier vpdContextApplier;

    @PersistenceContext
    private EntityManager entityManager;

    public UserRepositoryImpl(
            UserJpaRepository jpaRepository,
            UserEntityMapper mapper,
            VpdContextApplier vpdContextApplier) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.vpdContextApplier = vpdContextApplier;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByUsername(username).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findById(PersonId userId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findById(userId.value().toString()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsernameAndTenantId(String username, UUID tenantId) {
        vpdContextApplier.applyIfPresent(entityManager);
        return jpaRepository.findByUsernameAndTenantId(username, tenantId.toString())
                .map(mapper::toDomain);
    }
}
