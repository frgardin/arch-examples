package com.example.onboarding.usecase.gateway;

import com.example.onboarding.entity.OnboardingJob;

/**
 * Observer pattern: the orchestrator publishes progress; subscribers (SSE emitters) react.
 * Defined in Ring 2 so the orchestrator stays independent of SSE specifics.
 */
public interface ProgressPublisher {

    void publish(OnboardingJob job);
}
