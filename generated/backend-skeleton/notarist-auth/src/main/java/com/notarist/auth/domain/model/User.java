package com.notarist.auth.domain.model;

import com.notarist.core.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Domain model for authenticated user. */
public class User {

    private final PersonId userId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final Set<Role> roles;
    private final UUID tenantId;
    private final boolean active;
    private final Instant lastLoginAt;

    public User(
            PersonId userId,
            String username,
            String passwordHash,
            String fullName,
            Set<Role> roles,
            UUID tenantId,
            boolean active,
            Instant lastLoginAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.roles = Set.copyOf(roles);
        this.tenantId = tenantId;
        this.active = active;
        this.lastLoginAt = lastLoginAt;
    }

    public PersonId getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Set<Role> getRoles() { return roles; }
    public UUID getTenantId() { return tenantId; }
    public boolean isActive() { return active; }
    public Instant getLastLoginAt() { return lastLoginAt; }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
