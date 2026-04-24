package com.example.onboarding.infrastructure.async;

import com.example.onboarding.adapter.controller.response.JobProgressResponse;
import com.example.onboarding.entity.JobStatus;
import com.example.onboarding.entity.OnboardingJob;
import com.example.onboarding.usecase.gateway.ProgressPublisher;
import com.example.onboarding.usecase.progress.JobProgressOutput;
import org.springframework.stereotype.Component;

/**
 * Ring 3 adapter implementing the Ring 2 ProgressPublisher port. Translates a domain
 * OnboardingJob into the JSON payload the browser already consumes on the polling endpoint,
 * then hands it to the per-job emitter registry.
 *
 * The event name mirrors the job status so the frontend can attach typed listeners
 * (progress / completed / failed) instead of switching on a field.
 */
@Component
public class SseProgressPublisher implements ProgressPublisher {

    private final SseEmitterRegistry registry;

    public SseProgressPublisher(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void publish(OnboardingJob job) {
        JobProgressResponse payload = JobProgressResponse.from(JobProgressOutput.from(job));
        String eventName = switch (job.status()) {
            case COMPLETED            -> "completed";
            case FAILED               -> "failed";
            case PENDING, IN_PROGRESS -> "progress";
        };
        registry.broadcast(job.id(), eventName, payload);

        if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.FAILED) {
            registry.complete(job.id());
        }
    }
}
