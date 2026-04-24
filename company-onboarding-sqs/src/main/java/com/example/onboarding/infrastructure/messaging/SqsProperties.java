package com.example.onboarding.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed config for the AWS SDK v2 client. Binding "aws" prefix from application.yml.
 * Endpoint is optional: in real AWS it stays empty and the SDK uses the regional
 * endpoint by default; in LocalStack it points to http://localhost:4566.
 */
@ConfigurationProperties(prefix = "aws")
public record SqsProperties(
        String region,
        String endpoint,
        String accessKey,
        String secretKey,
        Sqs sqs
) {
    public record Sqs(
            String queueUrl,
            int waitTimeSeconds,
            int maxMessages,
            int consumerThreads
    ) { }
}
