package com.example.onboarding.entity;

import java.util.UUID;

/**
 * Ring 1 — tracks the async creation process as a first-class domain concept.
 * Mutable on purpose: progress evolves over time. State transitions live here,
 * not in a service, so the rules travel with the entity.
 */
public final class OnboardingJob {

    private final UUID id;
    private final String idempotencyKey;
    private final String companyName;
    private final int totalSteps;

    private JobStatus status;
    private int stepIndex;
    private String currentStep;
    private int percent;
    private String failedStep;
    private String errorMessage;
    private UUID companyId;
    private long version;

    public OnboardingJob(UUID id,
                         String idempotencyKey,
                         String companyName,
                         int totalSteps,
                         JobStatus status,
                         int stepIndex,
                         String currentStep,
                         int percent,
                         String failedStep,
                         String errorMessage,
                         UUID companyId,
                         long version) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.companyName = companyName;
        this.totalSteps = totalSteps;
        this.status = status;
        this.stepIndex = stepIndex;
        this.currentStep = currentStep;
        this.percent = percent;
        this.failedStep = failedStep;
        this.errorMessage = errorMessage;
        this.companyId = companyId;
        this.version = version;
    }

    public static OnboardingJob pending(String idempotencyKey, String companyName, int totalSteps) {
        return new OnboardingJob(
                UUID.randomUUID(), idempotencyKey, companyName, totalSteps,
                JobStatus.PENDING, 0, null, 0, null, null, null, 0L);
    }

    public void start() {
        if (status != JobStatus.PENDING) {
            throw new IllegalStateException("Job can only be started from PENDING, was " + status);
        }
        this.status = JobStatus.IN_PROGRESS;
    }

    public void progressTo(int stepIndex, String stepName, int percent) {
        if (status != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot progress a job in state " + status);
        }
        this.stepIndex = stepIndex;
        this.currentStep = stepName;
        this.percent = percent;
    }

    public void attachCompany(UUID companyId) {
        this.companyId = companyId;
    }

    public void complete() {
        this.status = JobStatus.COMPLETED;
        this.percent = 100;
        this.currentStep = null;
    }

    public void fail(String step, String message) {
        this.status = JobStatus.FAILED;
        this.failedStep = step;
        this.errorMessage = message;
    }

    public UUID id()              { return id; }
    public String idempotencyKey(){ return idempotencyKey; }
    public String companyName()   { return companyName; }
    public int totalSteps()       { return totalSteps; }
    public JobStatus status()     { return status; }
    public int stepIndex()        { return stepIndex; }
    public String currentStep()   { return currentStep; }
    public int percent()          { return percent; }
    public String failedStep()    { return failedStep; }
    public String errorMessage()  { return errorMessage; }
    public UUID companyId()       { return companyId; }
    public long version()         { return version; }

    /** Used by the JPA mapper when loading an existing row. */
    public void setVersion(long version) { this.version = version; }
}
