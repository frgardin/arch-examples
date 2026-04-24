package com.example.onboarding.adapter.gateway.persistence.mapper;

import com.example.onboarding.adapter.gateway.persistence.model.OnboardingJobJpa;
import com.example.onboarding.entity.OnboardingJob;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper: OnboardingJob uses a constructor with many args and has a setter only
 * for `version`, so MapStruct's default code generation is more hassle than this short class.
 */
@Component
public class OnboardingJobJpaMapper {

    public OnboardingJobJpa toJpa(OnboardingJob job) {
        OnboardingJobJpa jpa = new OnboardingJobJpa();
        jpa.setId(job.id());
        jpa.setIdempotencyKey(job.idempotencyKey());
        jpa.setCompanyName(job.companyName());
        jpa.setStatus(job.status());
        jpa.setStepIndex(job.stepIndex());
        jpa.setTotalSteps(job.totalSteps());
        jpa.setCurrentStep(job.currentStep());
        jpa.setPercent(job.percent());
        jpa.setFailedStep(job.failedStep());
        jpa.setErrorMessage(job.errorMessage());
        jpa.setCompanyId(job.companyId());
        jpa.setVersion(job.version());
        return jpa;
    }

    public OnboardingJob toDomain(OnboardingJobJpa jpa) {
        return new OnboardingJob(
                jpa.getId(),
                jpa.getIdempotencyKey(),
                jpa.getCompanyName(),
                jpa.getTotalSteps(),
                jpa.getStatus(),
                jpa.getStepIndex(),
                jpa.getCurrentStep(),
                jpa.getPercent(),
                jpa.getFailedStep(),
                jpa.getErrorMessage(),
                jpa.getCompanyId(),
                jpa.getVersion()
        );
    }
}
