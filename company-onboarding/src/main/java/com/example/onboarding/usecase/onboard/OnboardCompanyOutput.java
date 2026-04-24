package com.example.onboarding.usecase.onboard;

import com.example.onboarding.entity.JobStatus;

import java.util.UUID;

public record OnboardCompanyOutput(UUID jobId, JobStatus status, int totalSteps) { }
