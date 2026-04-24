package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.gateway.OnboardingJobDispatcher;
import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ring 3 adapter implementing the Ring 2 {@link OnboardingJobDispatcher} port.
 * Publishing lives outside any database transaction — we do not want a failed
 * publish to roll back the already-committed onboarding_job row. If the publish
 * fails, the job row is left in PENDING state, and a reaper could republish or
 * mark it FAILED.
 */
@Component
public class RabbitOnboardingDispatcher implements OnboardingJobDispatcher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitOnboardingDispatcher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void dispatch(UUID jobId, OnboardCompanyInput input) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY,
                new OnboardingJobMessage(jobId, input));
    }
}
