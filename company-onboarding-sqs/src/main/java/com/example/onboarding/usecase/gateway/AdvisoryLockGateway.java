package com.example.onboarding.usecase.gateway;

/**
 * Abstracts Postgres advisory locks so the use case layer stays DB-agnostic.
 * Implementation in Ring 3 calls pg_try_advisory_xact_lock(hashtext(key)).
 */
public interface AdvisoryLockGateway {

    /**
     * Attempts to acquire a transaction-scoped advisory lock on the given key.
     * The lock is automatically released at transaction commit/rollback.
     *
     * @return true if acquired, false if another transaction currently holds it
     */
    boolean tryAcquire(String key);
}
