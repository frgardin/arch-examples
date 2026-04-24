package com.example.onboarding.adapter.gateway.persistence;

import com.example.onboarding.adapter.gateway.persistence.mapper.OnboardingJobJpaMapper;
import com.example.onboarding.adapter.gateway.persistence.model.OnboardingJobJpa;
import com.example.onboarding.adapter.gateway.persistence.repository.OnboardingJobJpaRepository;
import com.example.onboarding.entity.OnboardingJob;
import com.example.onboarding.usecase.gateway.OnboardingJobGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class OnboardingJobGatewayJpa implements OnboardingJobGateway {

    private final OnboardingJobJpaRepository repository;
    private final OnboardingJobJpaMapper mapper;

    public OnboardingJobGatewayJpa(OnboardingJobJpaRepository repository,
                                   OnboardingJobJpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Writes happen in their own transaction: the orchestrator calls save() between async
     * steps and we want each progress update to become visible immediately, not at the end
     * of some enclosing scope.
     */
    @Override
    @Transactional
    public OnboardingJob save(OnboardingJob job) {
        OnboardingJobJpa saved = repository.save(mapper.toJpa(job));
        // Reflect bumped @Version back onto the domain object so the orchestrator's next
        // save() carries the correct value.
        job.setVersion(saved.getVersion());
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OnboardingJob> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OnboardingJob> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }
}
