package com.example.onboarding.adapter.gateway.persistence.model;

import com.example.onboarding.entity.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "onboarding_job")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingJobJpa {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps;

    @Column(name = "current_step")
    private String currentStep;

    @Column(nullable = false)
    private int percent;

    @Column(name = "failed_step")
    private String failedStep;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "company_id")
    private UUID companyId;

    @Version
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onInsert() { this.updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void onUpdate()  { this.updatedAt = OffsetDateTime.now(); }
}
