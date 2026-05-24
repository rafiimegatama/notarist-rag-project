package com.notarist.auth.infrastructure.persistence.oracle;

import com.notarist.auth.domain.model.Role;
import jakarta.persistence.*;

/**
 * PHASE 6A.2-FIX: roleCode changed from String to Role enum with @Enumerated(STRING).
 * UserRepositoryImpl / UserEntityMapper updated: remove .name()/.valueOf() for roleCode.
 */
@Entity
@Table(name = "USER_ROLE_MAP", schema = "NOTARIST")
public class UserRoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_role_seq")
    @SequenceGenerator(name = "user_role_seq", sequenceName = "NOTARIST.USER_ROLE_MAP_SEQ", allocationSize = 1)
    @Column(name = "ROLE_MAP_ID")
    private Long roleMapId;

    @Column(name = "USER_ID", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_CODE", length = 50, nullable = false)
    private Role roleCode;

    protected UserRoleJpaEntity() {}

    public UserRoleJpaEntity(String userId, Role roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public Long getRoleMapId() { return roleMapId; }
    public String getUserId() { return userId; }
    public Role getRoleCode() { return roleCode; }
}
