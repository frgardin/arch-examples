package com.example.onboarding.entity;

import java.util.UUID;

/**
 * Ring 1 — Enterprise Business Rule.
 * Pure Java: no Spring, no JPA, no Jackson. The framework-free heart of the aggregate.
 */
public record Company(UUID id, String name, String taxId) {
    public Company {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (taxId == null || taxId.isBlank()) {
            throw new IllegalArgumentException("Company taxId is required");
        }
    }

    public static Company create(String name, String taxId) {
        return new Company(UUID.randomUUID(), name, taxId);
    }
}
