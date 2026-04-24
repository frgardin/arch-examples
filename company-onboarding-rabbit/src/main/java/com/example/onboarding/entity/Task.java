package com.example.onboarding.entity;

import java.util.UUID;

public record Task(UUID id, UUID projectId, String title, String description) {
    public static Task create(UUID projectId, String title, String description) {
        return new Task(UUID.randomUUID(), projectId, title, description);
    }
}
