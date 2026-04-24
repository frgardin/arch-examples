package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.onboard.OnboardCompanyInput;

import java.util.UUID;

public record OnboardingJobMessage(UUID jobId, OnboardCompanyInput input) { }
