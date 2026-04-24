package com.example.onboarding.adapter.controller.response;

import com.example.onboarding.entity.JobStatus;

import java.util.UUID;

public record JobAcceptedResponse(UUID jobId, JobStatus status, int totalSteps, String streamUrl) { }
