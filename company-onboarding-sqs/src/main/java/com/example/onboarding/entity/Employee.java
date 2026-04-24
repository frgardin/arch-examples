package com.example.onboarding.entity;

import java.util.UUID;

public record Employee(UUID id, UUID departmentId, String name, String email) {
    public static Employee create(UUID departmentId, String name, String email) {
        return new Employee(UUID.randomUUID(), departmentId, name, email);
    }
}
