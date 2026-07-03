package com.notarist.core.domain.valueobject;

import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        if (value == null) throw new IllegalArgumentException("PersonId value must not be null");
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    public static PersonId generate() {
        return new PersonId(UUID.randomUUID());
    }

    public static PersonId from(String uuidString) {
        return new PersonId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
