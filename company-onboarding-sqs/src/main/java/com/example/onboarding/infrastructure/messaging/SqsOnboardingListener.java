package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.usecase.onboard.OnboardingWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-polling SQS consumer. Unlike RabbitMQ's push model, SQS is pull-based: we
 * hold one or more threads looping on {@code ReceiveMessage} with WaitTimeSeconds=20,
 * which is the server-side long-poll maximum. Empty receives cost nothing beyond
 * the TCP round-trip; this is much cheaper than short-polling in a tight loop.
 *
 * Lifecycle is tied to Spring via {@link SmartLifecycle}: threads start when the
 * context is ready and stop cleanly on shutdown (no runaway daemon).
 *
 * Semantics:
 *   - Successful processing → explicit DeleteMessage (SQS is at-least-once; no delete = redelivery).
 *   - Exception in worker → skip delete. SQS redelivers after VisibilityTimeout.
 *     After maxReceiveCount (set on the queue RedrivePolicy), the message lands
 *     in the DLQ automatically.
 *
 * Not shown for brevity but worth noting: for jobs that exceed VisibilityTimeout
 * the consumer should call ChangeMessageVisibility periodically (or configure a
 * visibility extender). Today's timeout (300s) fits a single onboarding run.
 */
@Component
public class SqsOnboardingListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsOnboardingListener.class);

    private final SqsClient sqsClient;
    private final SqsProperties props;
    private final ObjectMapper objectMapper;
    private final OnboardingWorker worker;

    private ExecutorService consumers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SqsOnboardingListener(SqsClient sqsClient,
                                 SqsProperties props,
                                 ObjectMapper objectMapper,
                                 OnboardingWorker worker) {
        this.sqsClient = sqsClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.worker = worker;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        int threads = props.sqs().consumerThreads();
        consumers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "sqs-onboarding-consumer-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < threads; i++) {
            consumers.submit(this::pollLoop);
        }
        log.info("SQS onboarding consumer started with {} thread(s)", threads);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        consumers.shutdownNow();
        try {
            consumers.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SQS onboarding consumer stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(props.sqs().queueUrl())
                        .waitTimeSeconds(props.sqs().waitTimeSeconds())
                        .maxNumberOfMessages(props.sqs().maxMessages())
                        .build()
                ).messages();

                for (Message m : messages) {
                    handle(m);
                }
            } catch (Exception e) {
                // Don't let a transient AWS/network error kill the loop. Brief pause
                // avoids hot-looping on a persistent failure.
                log.warn("SQS receive failed, backing off: {}", e.getMessage());
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handle(Message m) {
        try {
            OnboardingJobMessage payload = objectMapper.readValue(m.body(), OnboardingJobMessage.class);
            log.debug("Consuming onboarding job message id={}", payload.jobId());
            worker.process(payload.jobId(), payload.input());

            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(props.sqs().queueUrl())
                    .receiptHandle(m.receiptHandle())
                    .build());
        } catch (Exception e) {
            // Intentionally swallow: not calling delete means SQS will redeliver.
            // After maxReceiveCount redeliveries, the queue's RedrivePolicy moves
            // the message to the DLQ.
            log.error("Failed to process SQS message, letting SQS redeliver", e);
        }
    }
}
