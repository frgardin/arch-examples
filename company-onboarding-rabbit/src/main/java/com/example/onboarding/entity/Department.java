package com.example.onboarding.entity;

import java.util.UUID;

public record Department(UUID id, UUID companyId, String name) {
    public static Department create(UUID companyId, String name) {
        return new Department(UUID.randomUUID(), companyId, name);
    }
}
