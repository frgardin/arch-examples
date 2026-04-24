package com.example.onboarding.adapter.gateway.persistence;

import com.example.onboarding.adapter.gateway.persistence.repository.AdvisoryLockRepository;
import com.example.onboarding.usecase.gateway.AdvisoryLockGateway;
import org.springframework.stereotype.Component;

@Component
public class AdvisoryLockGatewayJpa implements AdvisoryLockGateway {

    private final AdvisoryLockRepository repository;

    public AdvisoryLockGatewayJpa(AdvisoryLockRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryAcquire(String key) {
        // Must run inside a transaction — the caller (CreateCompanyStep) already wraps this
        // in REQUIRES_NEW, which is the lock's lifecycle scope.
        return repository.tryAcquire(key);
    }
}
