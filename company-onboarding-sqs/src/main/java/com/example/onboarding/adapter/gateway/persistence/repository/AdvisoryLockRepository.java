package com.example.onboarding.adapter.gateway.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class AdvisoryLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Session-less, transaction-scoped advisory lock. Auto-released on tx commit/rollback.
     * hashtext() collapses the key to a 32-bit int suitable for pg_try_advisory_xact_lock.
     */
    public boolean tryAcquire(String key) {
        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtext(:k))")
                .setParameter("k", key)
                .getSingleResult();
        return (Boolean) result;
    }
}
