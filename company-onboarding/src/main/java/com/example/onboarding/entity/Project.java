package com.example.onboarding.entity;

import java.util.UUID;

public record Project(UUID id, UUID companyId, String name) {
    public static Project create(UUID companyId, String name) {
        return new Project(UUID.randomUUID(), companyId, name);
    }
}
