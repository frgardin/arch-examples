package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.onboard.OnboardCompanyInput;

import java.util.UUID;

/**
 * Wire format for the "start this job" signal. Carrying the full OnboardCompanyInput
 * keeps the worker self-sufficient: no need to persist the payload in the DB just to
 * re-read it on the consumer side.
 *
 * Trade-off: messages get larger (tens to hundreds of KB for rich payloads). If that
 * becomes a problem, store the input in a `job_payload` table keyed by jobId and
 * carry only the jobId in the message.
 */
public record OnboardingJobMessage(UUID jobId, OnboardCompanyInput input) { }
