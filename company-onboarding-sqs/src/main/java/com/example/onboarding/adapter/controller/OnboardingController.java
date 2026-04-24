package com.example.onboarding.adapter.controller;

import com.example.onboarding.adapter.controller.request.OnboardCompanyRequest;
import com.example.onboarding.adapter.controller.response.JobAcceptedResponse;
import com.example.onboarding.adapter.presenter.OnboardCompanyJsonPresenter;
import com.example.onboarding.usecase.onboard.OnboardCompany;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class OnboardingController {

    private final OnboardCompany onboardCompany;

    public OnboardingController(OnboardCompany onboardCompany) {
        this.onboardCompany = onboardCompany;
    }

    @PostMapping("/onboard")
    public ResponseEntity<JobAcceptedResponse> onboard(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnboardCompanyRequest request) {

        var presenter = new OnboardCompanyJsonPresenter();
        onboardCompany.execute(request.toInput(idempotencyKey), presenter);
        return presenter.response();
    }
}
