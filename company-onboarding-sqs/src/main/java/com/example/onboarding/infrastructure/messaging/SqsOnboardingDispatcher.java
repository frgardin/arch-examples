package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.gateway.OnboardingJobDispatcher;
import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

/**
 * Ring 3 adapter implementing {@link OnboardingJobDispatcher}. SQS has no native JSON
 * marshalling the way AMQP's converter does — we serialize with Jackson explicitly.
 *
 * Publishing happens outside any DB transaction; a failed publish leaves the job in
 * PENDING state and does not roll back the job row. A reaper would be responsible
 * for republishing or marking orphaned jobs FAILED.
 */
@Component
public class SqsOnboardingDispatcher implements OnboardingJobDispatcher {

    private final SqsClient sqsClient;
    private final SqsProperties props;
    private final ObjectMapper objectMapper;

    public SqsOnboardingDispatcher(SqsClient sqsClient,
                                   SqsProperties props,
                                   ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void dispatch(UUID jobId, OnboardCompanyInput input) {
        try {
            String body = objectMapper.writeValueAsString(new OnboardingJobMessage(jobId, input));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(props.sqs().queueUrl())
                    .messageBody(body)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize onboarding job message", e);
        }
    }
}
