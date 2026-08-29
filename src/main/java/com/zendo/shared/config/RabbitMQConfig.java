package com.zendo.shared.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange zendoExchange() {
        return new TopicExchange("zendo.topic");
    }

    @Bean
    public Queue cartOrderEventsQueue() {
        return new Queue("cart.order.events.queue");
    }

    @Bean
    public Binding cartOrderEventsBinding(Queue cartOrderEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(cartOrderEventsQueue).to(zendoExchange).with("order.#");
    }

    @Bean
    public Queue paymentOrderEventsQueue() {
        return new Queue("payment.order.events.queue");
    }

    @Bean
    public Binding paymentOrderEventsBinding(Queue paymentOrderEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(paymentOrderEventsQueue).to(zendoExchange).with("order.#");
    }

    @Bean
    public Queue paymentEventsQueue() {
        return new Queue("payment.events");
    }

    @Bean
    public Binding paymentEventsBinding(Queue paymentEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(paymentEventsQueue).to(zendoExchange).with("payment.#");
    }

    @Bean
    public Queue orderPaymentEventsQueue() {
        return new Queue("order.payment.events.queue");
    }

    @Bean
    public Binding orderPaymentEventsBinding(Queue orderPaymentEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(orderPaymentEventsQueue).to(zendoExchange).with("payment.#");
    }

    // Notification module queues
    @Bean
    public Queue notificationOrderEventsQueue() {
        return new Queue("notification.order.events.queue");
    }

    @Bean
    public Binding notificationOrderEventsBinding(Queue notificationOrderEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(notificationOrderEventsQueue).to(zendoExchange).with("order.#");
    }

    @Bean
    public Queue notificationPaymentEventsQueue() {
        return new Queue("notification.payment.events.queue");
    }

    @Bean
    public Binding notificationPaymentEventsBinding(Queue notificationPaymentEventsQueue, TopicExchange zendoExchange) {
        return BindingBuilder.bind(notificationPaymentEventsQueue).to(zendoExchange).with("payment.#");
    }
}
