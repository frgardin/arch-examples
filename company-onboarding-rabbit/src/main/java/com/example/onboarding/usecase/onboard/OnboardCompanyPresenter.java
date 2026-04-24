package com.example.onboarding.usecase.onboard;

/**
 * Output boundary (Ring 2). A new job accepted vs. an idempotent replay of an existing one
 * are different outcomes for the caller (202 vs 200), so they get separate methods.
 */
public interface OnboardCompanyPresenter {

    void presentAccepted(OnboardCompanyOutput output);

    void presentAlreadyExists(OnboardCompanyOutput output);
}
