package com.notarist.auth.application.port.out;

import com.notarist.auth.domain.model.User;
import com.notarist.core.domain.valueobject.PersonId;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    Optional<User> findById(PersonId userId);
    Optional<User> findByUsernameAndTenantId(String username, UUID tenantId);
}
