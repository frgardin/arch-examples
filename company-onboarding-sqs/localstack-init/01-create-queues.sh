#!/bin/sh
# LocalStack init hook — runs once when container is ready.
# Creates the DLQ first so the main queue can reference it via RedrivePolicy.
set -e

awslocal sqs create-queue \
    --queue-name onboarding-start-dlq

DLQ_ARN=$(awslocal sqs get-queue-attributes \
    --queue-url http://localhost:4566/000000000000/onboarding-start-dlq \
    --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue \
    --queue-name onboarding-start-queue \
    --attributes "{
        \"VisibilityTimeout\": \"300\",
        \"MessageRetentionPeriod\": \"1209600\",
        \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
    }"

echo "SQS queues ready: onboarding-start-queue (DLQ after 3 receives → onboarding-start-dlq)"
