package com.example.onboarding.usecase.onboard.step;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template Method base for a single creation phase.
 *
 * execute() is the fixed algorithm skeleton (validate → doExecute), marked with
 * {@code REQUIRES_NEW} so every step commits independently — partial progress
 * survives a later failure and is visible to progress readers mid-flight.
 *
 * Not final on purpose: Spring's CGLIB proxies cannot intercept final methods,
 * and @Transactional relies on the proxy.
 */
public abstract class CreationStep {

    public abstract int order();

    public abstract String name();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(CreationStepContext ctx) {
        validate(ctx);
        doExecute(ctx);
    }

    protected void validate(CreationStepContext ctx) {
        // hook for subclasses; no-op by default
    }

    protected abstract void doExecute(CreationStepContext ctx);
}
