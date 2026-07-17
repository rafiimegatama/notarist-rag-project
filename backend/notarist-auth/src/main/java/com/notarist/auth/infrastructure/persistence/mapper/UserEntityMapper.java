package com.notarist.auth.infrastructure.persistence.mapper;

import com.notarist.auth.domain.model.Role;
import com.notarist.auth.domain.model.User;
import com.notarist.auth.infrastructure.persistence.postgres.UserJpaEntity;
import com.notarist.auth.infrastructure.persistence.postgres.UserRoleJpaEntity;
import com.notarist.core.domain.valueobject.PersonId;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserEntityMapper {

    public User toDomain(UserJpaEntity entity) {
        Set<Role> roles = entity.getRoles().stream()
                .map(r -> Role.valueOf(r.getRoleCode()))
                .collect(Collectors.toSet());

        return new User(
                new PersonId(UUID.fromString(entity.getUserId())),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getFullName(),
                roles,
                UUID.fromString(entity.getTenantId()),
                entity.isActive(),
                entity.getLastLoginAt()
        );
    }

    public UserJpaEntity toEntity(User domain) {
        UserJpaEntity entity = new UserJpaEntity(
                domain.getUserId().value().toString(),
                domain.getTenantId().toString(),
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getFullName(),
                domain.isActive(),
                domain.getLastLoginAt(),
                domain.getLastLoginAt()
        );
        domain.getRoles().forEach(role ->
                entity.getRoles().add(new UserRoleJpaEntity(
                        domain.getUserId().value().toString(),
                        role.name()))
        );
        return entity;
    }
}
