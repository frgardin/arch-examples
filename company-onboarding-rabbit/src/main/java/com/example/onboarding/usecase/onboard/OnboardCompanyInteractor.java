package com.example.onboarding.usecase.onboard;

import com.example.onboarding.entity.OnboardingJob;
import com.example.onboarding.usecase.gateway.OnboardingJobDispatcher;
import com.example.onboarding.usecase.gateway.OnboardingJobGateway;
import org.springframework.stereotype.Service;

/**
 * Thin orchestrator now. Responsibilities pruned to:
 *   - idempotency replay
 *   - creating the job row (partial unique index enforces "one active per company")
 *   - handing the job off to the dispatcher (queue publish, in this variant)
 *
 * The actual step-by-step execution lives in {@link OnboardingWorker}, invoked later
 * by the message listener.
 */
@Service
public class OnboardCompanyInteractor implements OnboardCompany {

    private final OnboardingJobGateway jobGateway;
    private final OnboardingJobDispatcher dispatcher;
    private final OnboardingWorker worker;

    public OnboardCompanyInteractor(OnboardingJobGateway jobGateway,
                                    OnboardingJobDispatcher dispatcher,
                                    OnboardingWorker worker) {
        this.jobGateway = jobGateway;
        this.dispatcher = dispatcher;
        this.worker = worker;
    }

    @Override
    public void execute(OnboardCompanyInput input, OnboardCompanyPresenter presenter) {
        var existing = jobGateway.findByIdempotencyKey(input.idempotencyKey());
        if (existing.isPresent()) {
            var job = existing.get();
            presenter.presentAlreadyExists(new OnboardCompanyOutput(
                    job.id(), job.status(), job.totalSteps()));
            return;
        }

        OnboardingJob job = OnboardingJob.pending(
                input.idempotencyKey(), input.companyName(), worker.totalSteps());
        OnboardingJob saved = jobGateway.save(job);

        // Fire the message AFTER the job row is committed so the consumer can
        // always find it. Spring's @Transactional around save() commits before
        // this line returns; publish is plain, not part of any tx.
        dispatcher.dispatch(saved.id(), input);

        presenter.presentAccepted(new OnboardCompanyOutput(
                saved.id(), saved.status(), saved.totalSteps()));
    }
}
