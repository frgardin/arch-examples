package com.example.onboarding.usecase.onboard.step;

import com.example.onboarding.entity.Company;
import com.example.onboarding.usecase.gateway.AdvisoryLockGateway;
import com.example.onboarding.usecase.gateway.CompanyGateway;
import org.springframework.stereotype.Component;

@Component
public class CreateCompanyStep extends CreationStep {

    private final CompanyGateway companyGateway;
    private final AdvisoryLockGateway lockGateway;

    public CreateCompanyStep(CompanyGateway companyGateway, AdvisoryLockGateway lockGateway) {
        this.companyGateway = companyGateway;
        this.lockGateway = lockGateway;
    }

    @Override public int order() { return 1; }
    @Override public String name() { return "create-company"; }

    @Override
    protected void doExecute(CreationStepContext ctx) {
        // Advisory lock: serializes concurrent onboardings with the same company name at
        // the moment the root row is being created. pg_try_advisory_xact_lock auto-releases
        // when this step's REQUIRES_NEW transaction commits. The partial unique index on
        // onboarding_job is the broader guard; this narrows the race window further.
        String name = ctx.input().companyName();
        if (!lockGateway.tryAcquire("company:" + name)) {
            throw new IllegalStateException("Another onboarding for '" + name + "' is in progress");
        }

        Company saved = companyGateway.saveCompany(Company.create(name, ctx.input().taxId()));
        ctx.setCompanyId(saved.id());
    }
}
