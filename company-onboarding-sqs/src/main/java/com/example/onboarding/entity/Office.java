package com.example.onboarding.entity;

import java.util.UUID;

public record Office(UUID id, UUID companyId, String name, String city) {
    public static Office create(UUID companyId, String name, String city) {
        return new Office(UUID.randomUUID(), companyId, name, city);
    }
}
