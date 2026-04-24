package com.example.onboarding.adapter.controller.response;

import com.example.onboarding.entity.JobStatus;
import com.example.onboarding.usecase.progress.JobProgressOutput;

import java.util.UUID;

public record JobProgressResponse(
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
    public static JobProgressResponse from(JobProgressOutput o) {
        return new JobProgressResponse(
                o.jobId(), o.status(), o.stepIndex(), o.totalSteps(),
                o.currentStep(), o.percent(), o.failedStep(),
                o.errorMessage(), o.companyId());
    }
}
