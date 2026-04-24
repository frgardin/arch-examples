package com.example.onboarding.usecase.onboard;

/**
 * Input boundary (Ring 2). Controllers depend on this interface, not the interactor.
 * Uses the Presenter pattern: the use case doesn't return a value — it hands the
 * Output to the presenter, which owns view-model formatting.
 */
public interface OnboardCompany {
    void execute(OnboardCompanyInput input, OnboardCompanyPresenter presenter);
}
