package com.example.onboarding.adapter.gateway.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "room")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomJpa {
    @Id
    private UUID id;

    @Column(name = "office_id", nullable = false)
    private UUID officeId;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false)
    private String number;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
