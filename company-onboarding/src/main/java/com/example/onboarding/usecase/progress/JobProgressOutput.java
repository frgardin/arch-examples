package com.example.onboarding.usecase.progress;

import com.example.onboarding.entity.JobStatus;
import com.example.onboarding.entity.OnboardingJob;

import java.util.UUID;

public record JobProgressOutput(
        UUID jobId,
        JobStatus status,
        int stepIndex,
        int totalSteps,
        String currentStep,
        int percent,
        String failedStep,
        String errorMessage,
        UUID companyId
) {
    public static JobProgressOutput from(OnboardingJob job) {
        return new JobProgressOutput(
                job.id(), job.status(), job.stepIndex(), job.totalSteps(),
                job.currentStep(), job.percent(), job.failedStep(),
                job.errorMessage(), job.companyId());
    }
}
