package com.notarist.auth.infrastructure.persistence.oracle;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "NOTARIST_USER", schema = "NOTARIST")
public class UserJpaEntity {

    @Id
    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Column(name = "TENANT_ID", length = 36, nullable = false)
    private String tenantId;

    @Column(name = "USERNAME", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "PASSWORD_HASH", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "FULL_NAME", length = 255, nullable = false)
    private String fullName;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "LAST_LOGIN_AT")
    private Instant lastLoginAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "USER_ID", referencedColumnName = "USER_ID")
    private List<UserRoleJpaEntity> roles = new ArrayList<>();

    protected UserJpaEntity() {}

    public UserJpaEntity(
            String userId, String tenantId, String username, String passwordHash,
            String fullName, boolean active, Instant lastLoginAt, Instant createdAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.active = active;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
    }

    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public boolean isActive() { return active; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public List<UserRoleJpaEntity> getRoles() { return roles; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
