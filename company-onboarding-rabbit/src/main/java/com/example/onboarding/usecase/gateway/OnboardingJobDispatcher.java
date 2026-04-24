package com.example.onboarding.usecase.gateway;

import com.example.onboarding.usecase.onboard.OnboardCompanyInput;

import java.util.UUID;

/**
 * Ring 2 output port: how the interactor hands a freshly-created job off for async
 * execution. The in-process / RabbitMQ / SQS variants each implement this interface,
 * so the interactor stays unaware of the transport.
 */
public interface OnboardingJobDispatcher {

    void dispatch(UUID jobId, OnboardCompanyInput input);
}
