package com.example.onboarding.adapter.controller;

import com.example.onboarding.adapter.controller.response.JobProgressResponse;
import com.example.onboarding.entity.JobStatus;
import com.example.onboarding.infrastructure.async.SseEmitterRegistry;
import com.example.onboarding.usecase.progress.GetJobProgress;
import com.example.onboarding.usecase.progress.JobProgressOutput;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 min

    private final GetJobProgress getJobProgress;
    private final SseEmitterRegistry emitterRegistry;

    public JobController(GetJobProgress getJobProgress, SseEmitterRegistry emitterRegistry) {
        this.getJobProgress = getJobProgress;
        this.emitterRegistry = emitterRegistry;
    }

    /** Polling fallback — same response shape as SSE payloads. */
    @GetMapping("/{id}")
    public ResponseEntity<JobProgressResponse> get(@PathVariable UUID id) {
        return getJobProgress.byId(id)
                .map(JobProgressResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * SSE stream. Subscribes the new emitter to the registry BEFORE sending the initial event
     * to avoid a missed-event race with the orchestrator: any publish that lands between "emitter
     * created" and "initial snapshot sent" still reaches the client.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitterRegistry.register(id, emitter);

        getJobProgress.byId(id).ifPresent(output -> {
            try {
                emitter.send(initialEvent(output));
                if (isTerminal(output.status())) {
                    emitterRegistry.complete(id);
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static SseEmitter.SseEventBuilder initialEvent(JobProgressOutput o) {
        JobProgressResponse payload = JobProgressResponse.from(o);
        String eventName = switch (o.status()) {
            case COMPLETED       -> "completed";
            case FAILED          -> "failed";
            case PENDING, IN_PROGRESS -> "progress";
        };
        return SseEmitter.event()
                .id(String.valueOf(System.currentTimeMillis()))
                .name(eventName)
                .data(payload);
    }

    private static boolean isTerminal(JobStatus s) {
        return s == JobStatus.COMPLETED || s == JobStatus.FAILED;
    }
}
