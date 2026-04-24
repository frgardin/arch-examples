package com.example.onboarding.adapter.presenter;

import com.example.onboarding.adapter.controller.response.JobAcceptedResponse;
import com.example.onboarding.usecase.onboard.OnboardCompanyOutput;
import com.example.onboarding.usecase.onboard.OnboardCompanyPresenter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Presenter instance is created per-request by the controller and receives the Output.
 * It holds the resulting ResponseEntity for the controller to return.
 *
 * Why 202 vs 200: a freshly accepted job triggers background work, so the correct semantic
 * is 202 Accepted; an idempotency replay does no new work, so 200 OK.
 */
public class OnboardCompanyJsonPresenter implements OnboardCompanyPresenter {

    private ResponseEntity<JobAcceptedResponse> response;

    @Override
    public void presentAccepted(OnboardCompanyOutput output) {
        this.response = ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(toBody(output));
    }

    @Override
    public void presentAlreadyExists(OnboardCompanyOutput output) {
        this.response = ResponseEntity
                .status(HttpStatus.OK)
                .body(toBody(output));
    }

    public ResponseEntity<JobAcceptedResponse> response() {
        return response;
    }

    private static JobAcceptedResponse toBody(OnboardCompanyOutput o) {
        return new JobAcceptedResponse(
                o.jobId(),
                o.status(),
                o.totalSteps(),
                "/api/v1/jobs/" + o.jobId() + "/stream");
    }
}
