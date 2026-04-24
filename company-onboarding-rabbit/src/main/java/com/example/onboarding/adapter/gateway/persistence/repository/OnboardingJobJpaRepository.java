package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.OnboardingJobJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingJobJpaRepository extends JpaRepository<OnboardingJobJpa, UUID> {
    Optional<OnboardingJobJpa> findByIdempotencyKey(String idempotencyKey);
}
