package com.example.onboarding.usecase.onboard;

import com.example.onboarding.entity.OnboardingJob;
import com.example.onboarding.usecase.gateway.OnboardingJobGateway;
import com.example.onboarding.usecase.gateway.ProgressPublisher;
import com.example.onboarding.usecase.onboard.step.CreationStep;
import com.example.onboarding.usecase.onboard.step.CreationStepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The orchestrator. Responsible for:
 *   - idempotency replay
 *   - creating the job row (which also enforces "one active onboarding per company name" via a partial unique index)
 *   - submitting the step-by-step execution to the virtual-thread executor
 *   - publishing progress between steps
 *
 * The steps themselves are independent Spring beans (Strategy + Template Method),
 * sorted here once at construction time.
 */
@Service
public class OnboardCompanyInteractor implements OnboardCompany {

    private static final Logger log = LoggerFactory.getLogger(OnboardCompanyInteractor.class);

    private final OnboardingJobGateway jobGateway;
    private final List<CreationStep> steps;
    private final ProgressPublisher progressPublisher;
    private final Executor orchestratorExecutor;

    public OnboardCompanyInteractor(OnboardingJobGateway jobGateway,
                                    List<CreationStep> steps,
                                    ProgressPublisher progressPublisher,
                                    @Qualifier("orchestratorExecutor") Executor orchestratorExecutor) {
        this.jobGateway = jobGateway;
        this.steps = steps.stream()
                .sorted(Comparator.comparingInt(CreationStep::order))
                .toList();
        this.progressPublisher = progressPublisher;
        this.orchestratorExecutor = orchestratorExecutor;
    }

    @Override
    public void execute(OnboardCompanyInput input, OnboardCompanyPresenter presenter) {
        // Idempotency replay: same Idempotency-Key returns the same job, never creates a duplicate.
        var existing = jobGateway.findByIdempotencyKey(input.idempotencyKey());
        if (existing.isPresent()) {
            var job = existing.get();
            presenter.presentAlreadyExists(new OnboardCompanyOutput(
                    job.id(), job.status(), job.totalSteps()));
            return;
        }

        OnboardingJob job = OnboardingJob.pending(
                input.idempotencyKey(), input.companyName(), steps.size());
        // Can throw on the partial unique index (company_name where status in active)
        // — that surfaces as 409 at the controller boundary.
        OnboardingJob saved = jobGateway.save(job);

        orchestratorExecutor.execute(() -> runSafely(saved.id(), input));

        presenter.presentAccepted(new OnboardCompanyOutput(
                saved.id(), saved.status(), saved.totalSteps()));
    }

    private void runSafely(UUID jobId, OnboardCompanyInput input) {
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
                step.execute(ctx); // each step runs in its own REQUIRES_NEW transaction
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
