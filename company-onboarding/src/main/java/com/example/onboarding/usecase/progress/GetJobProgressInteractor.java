package com.example.onboarding.usecase.progress;

import com.example.onboarding.usecase.gateway.OnboardingJobGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetJobProgressInteractor implements GetJobProgress {

    private final OnboardingJobGateway jobGateway;

    public GetJobProgressInteractor(OnboardingJobGateway jobGateway) {
        this.jobGateway = jobGateway;
    }

    @Override
    public Optional<JobProgressOutput> byId(UUID jobId) {
        return jobGateway.findById(jobId).map(JobProgressOutput::from);
    }
}
