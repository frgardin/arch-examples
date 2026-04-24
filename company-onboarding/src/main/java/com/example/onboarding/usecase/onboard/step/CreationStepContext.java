package com.example.onboarding.usecase.onboard.step;

import com.example.onboarding.usecase.onboard.OnboardCompanyInput;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable shared state passed between steps. The only "fact" a later step needs from an
 * earlier one is the generated companyId; a map covers any future extensions without
 * changing signatures.
 */
public final class CreationStepContext {

    private final OnboardCompanyInput input;
    private final Map<String, Object> values = new HashMap<>();

    public CreationStepContext(OnboardCompanyInput input) {
        this.input = input;
    }

    public OnboardCompanyInput input() {
        return input;
    }

    public void setCompanyId(UUID id) { values.put("companyId", id); }
    public UUID companyId()           { return (UUID) values.get("companyId"); }
}
