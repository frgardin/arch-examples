package com.example.onboarding.usecase.gateway;

import com.example.onboarding.entity.OnboardingJob;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingJobGateway {

    OnboardingJob save(OnboardingJob job);

    Optional<OnboardingJob> findById(UUID id);

    Optional<OnboardingJob> findByIdempotencyKey(String idempotencyKey);
}
