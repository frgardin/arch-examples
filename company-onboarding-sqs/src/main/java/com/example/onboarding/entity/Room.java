package com.example.onboarding.entity;

import java.util.UUID;

public record Room(UUID id, UUID officeId, int floor, String number) {
    public static Room create(UUID officeId, int floor, String number) {
        return new Room(UUID.randomUUID(), officeId, floor, number);
    }
}
