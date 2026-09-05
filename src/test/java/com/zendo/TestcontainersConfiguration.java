package com.zendo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    PostgreSQLContainer<?> postgreSQLContainer() {
        return TestcontainersEnvironmentPostProcessor.POSTGRES;
    }

    @Bean
    RabbitMQContainer rabbitMQContainer() {
        return TestcontainersEnvironmentPostProcessor.RABBITMQ;
    }
}
