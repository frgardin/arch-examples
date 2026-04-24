package com.example.onboarding.infrastructure.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Single AWS SqsClient. The SDK v2 client is thread-safe and expected to be reused.
 * LocalStack path overrides the endpoint; in real AWS we pass through the default
 * resolution (regional endpoint + default credentials chain).
 */
@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(SqsProperties props) {
        var builder = SqsClient.builder()
                .region(Region.of(props.region()));

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create(props.accessKey(), props.secretKey())));
        }
        return builder.build();
    }
}
