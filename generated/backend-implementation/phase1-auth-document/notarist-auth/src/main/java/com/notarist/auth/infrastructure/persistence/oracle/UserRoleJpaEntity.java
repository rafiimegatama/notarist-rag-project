package com.notarist.auth.infrastructure.persistence.oracle;

import jakarta.persistence.*;

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

    @Column(name = "ROLE_CODE", length = 50, nullable = false)
    private String roleCode;

    protected UserRoleJpaEntity() {}

    public UserRoleJpaEntity(String userId, String roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public Long getRoleMapId() { return roleMapId; }
    public String getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }
}
