package com.example.onboarding.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarative topology. Spring AMQP creates these at startup (idempotent — declaring
 * an existing exchange/queue with the same args is a no-op).
 *
 * Topology:
 *     onboarding.exchange  ──► onboarding.start.queue       (live work)
 *     onboarding.dlx       ──► onboarding.start.dlq         (poison messages)
 *
 * Why a DLQ: default listener config (see application.yml) does not requeue on
 * exception. Without a DLX, failed messages vanish silently. With it, failures
 * land in the DLQ where they can be inspected and replayed by hand.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE    = "onboarding.exchange";
    public static final String QUEUE       = "onboarding.start.queue";
    public static final String ROUTING_KEY = "onboarding.start";

    public static final String DLX         = "onboarding.dlx";
    public static final String DLQ         = "onboarding.start.dlq";
    public static final String DLQ_RK      = "onboarding.start.dead";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue startQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_RK)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding startBinding(Queue startQueue, DirectExchange exchange) {
        return BindingBuilder.bind(startQueue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_RK);
    }

    /** JSON on the wire — message payload is a record, Jackson serializes cleanly. */
    @Bean
    public MessageConverter jacksonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        return t;
    }
}
