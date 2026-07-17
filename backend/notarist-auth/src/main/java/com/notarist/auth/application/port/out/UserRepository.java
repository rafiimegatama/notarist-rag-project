package com.notarist.auth.application.port.out;

import com.notarist.auth.domain.model.User;
import com.notarist.core.domain.valueobject.PersonId;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    /**
     * Tenant-scoped lookup by id. There is deliberately no bare {@code findById(PersonId)}: the
     * VPD tenant policy is fail-closed, so an id-only lookup would silently return nothing unless
     * the caller happened to hold a principal — a trap. Pass the tenant you already know.
     */
    Optional<User> findByIdAndTenantId(PersonId userId, UUID tenantId);
    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);
}
