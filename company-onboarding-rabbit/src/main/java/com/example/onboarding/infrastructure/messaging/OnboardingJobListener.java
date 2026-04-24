package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.onboard.OnboardingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes from {@code onboarding.start.queue}. The listener runs on a RabbitMQ
 * consumer thread; the worker's step execution (REQUIRES_NEW per step) is
 * independent of whatever transaction the AMQP container might have around the
 * acknowledge — the auto-ack happens when this method returns normally.
 *
 * If the worker throws, Spring AMQP re-raises, the message is nacked without
 * requeue (see application.yml), and the broker dead-letters it to
 * {@code onboarding.start.dlq}. Since {@code OnboardingWorker.process} catches
 * everything and marks the job FAILED itself, exceptions here should be rare
 * (serialization issues, OOM, etc.).
 */
@Component
public class OnboardingJobListener {

    private static final Logger log = LoggerFactory.getLogger(OnboardingJobListener.class);

    private final OnboardingWorker worker;

    public OnboardingJobListener(OnboardingWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onMessage(OnboardingJobMessage message) {
        log.debug("Consuming onboarding job message id={}", message.jobId());
        worker.process(message.jobId(), message.input());
    }
}
