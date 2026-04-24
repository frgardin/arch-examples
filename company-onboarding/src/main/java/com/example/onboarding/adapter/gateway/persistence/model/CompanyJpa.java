package com.example.onboarding.adapter.gateway.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "company")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyJpa {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "tax_id", nullable = false)
    private String taxId;

    @Version
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
