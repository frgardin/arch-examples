package com.example.onboarding.usecase.progress;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only use case. For pure queries we skip the Presenter boundary to keep the
 * Clean Architecture ceremony proportional — noting the pragmatic break on purpose.
 */
public interface GetJobProgress {
    Optional<JobProgressOutput> byId(UUID jobId);
}
