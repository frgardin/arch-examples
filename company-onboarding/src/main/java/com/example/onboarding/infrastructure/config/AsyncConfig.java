package com.example.onboarding.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Virtual-thread executor dedicated to onboarding orchestration. A named bean keeps the
 * interactor decoupled from Spring's default async machinery and makes it obvious that
 * these tasks run on their own pool — easy to swap for a platform-thread pool later
 * without touching the use case.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "orchestratorExecutor")
    public Executor orchestratorExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("onboarding-orchestrator-", 0).factory());
    }
}
