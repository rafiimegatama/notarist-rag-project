package com.notarist.auth.infrastructure.persistence.postgres;

import jakarta.persistence.*;

@Entity
@Table(name = "USER_ROLE_MAP")
public class UserRoleJpaEntity {

    // Was an Oracle SEQUENCE (USER_ROLE_MAP_SEQ) with allocationSize=1 — a DB round trip per
    // insert. The PostgreSQL column is GENERATED ALWAYS AS IDENTITY (Flyway V8), which has the
    // same semantics without a separate sequence object to keep in step with the table.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
