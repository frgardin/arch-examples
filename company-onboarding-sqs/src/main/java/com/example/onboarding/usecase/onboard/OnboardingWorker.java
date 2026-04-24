package com.example.onboarding.usecase.onboard;

import com.example.onboarding.entity.OnboardingJob;
import com.example.onboarding.usecase.gateway.OnboardingJobGateway;
import com.example.onboarding.usecase.gateway.ProgressPublisher;
import com.example.onboarding.usecase.onboard.step.CreationStep;
import com.example.onboarding.usecase.onboard.step.CreationStepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure orchestration. Invoked by whichever infrastructure adapter picked up the
 * job (the SQS poller, in this variant). Knows nothing about how it got here —
 * same contract whether the trigger is RabbitMQ, SQS or an in-process executor.
 */
@Service
public class OnboardingWorker {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWorker.class);

    private final OnboardingJobGateway jobGateway;
    private final List<CreationStep> steps;
    private final ProgressPublisher progressPublisher;

    public OnboardingWorker(OnboardingJobGateway jobGateway,
                            List<CreationStep> steps,
                            ProgressPublisher progressPublisher) {
        this.jobGateway = jobGateway;
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(CreationStep::order))
                .toList();
        this.progressPublisher = progressPublisher;
    }

    public int totalSteps() {
        return steps.size();
    }

    public void process(UUID jobId, OnboardCompanyInput input) {
        try {
            run(jobId, input);
        } catch (Throwable t) {
            log.error("Unexpected orchestrator error for job {}", jobId, t);
            jobGateway.findById(jobId).ifPresent(j -> {
                j.fail("orchestrator", t.getMessage());
                OnboardingJob saved = jobGateway.save(j);
                progressPublisher.publish(saved);
            });
        }
    }

    private void run(UUID jobId, OnboardCompanyInput input) {
        OnboardingJob job = jobGateway.findById(jobId).orElseThrow();
        job.start();
        job = jobGateway.save(job);
        progressPublisher.publish(job);

        var ctx = new CreationStepContext(input);

        for (int i = 0; i < steps.size(); i++) {
            CreationStep step = steps.get(i);
            try {
                step.execute(ctx);
            } catch (Exception e) {
                log.warn("Step {} failed for job {}", step.name(), jobId, e);
                job = jobGateway.findById(jobId).orElseThrow();
                job.fail(step.name(), rootMessage(e));
                job = jobGateway.save(job);
                progressPublisher.publish(job);
                return;
            }

            int percent = ((i + 1) * 100) / steps.size();
            job = jobGateway.findById(jobId).orElseThrow();
            if (i == 0 && ctx.companyId() != null) {
                job.attachCompany(ctx.companyId());
            }
            job.progressTo(i + 1, step.name(), percent);
            job = jobGateway.save(job);
            progressPublisher.publish(job);
        }

        job = jobGateway.findById(jobId).orElseThrow();
        job.complete();
        job = jobGateway.save(job);
        progressPublisher.publish(job);
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage();
    }
}
