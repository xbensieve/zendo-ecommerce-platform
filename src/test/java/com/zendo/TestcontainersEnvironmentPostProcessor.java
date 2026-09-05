package com.zendo;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.HashMap;
import java.util.Map;

public class TestcontainersEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("zendo_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "max_connections=300");

    public static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-management-alpine");

    public static final RedisContainer REDIS = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.asCanonicalNameString());

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!RABBITMQ.isRunning()) {
            RABBITMQ.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }

        Map<String, Object> props = new HashMap<>();
        props.put("DB_HOST", POSTGRES.getHost());
        props.put("DB_PORT", POSTGRES.getFirstMappedPort());
        props.put("DB_NAME", POSTGRES.getDatabaseName());
        props.put("DB_USER", POSTGRES.getUsername());
        props.put("DB_PASSWORD", POSTGRES.getPassword());
        props.put("RABBITMQ_HOST", RABBITMQ.getHost());
        props.put("RABBITMQ_PORT", RABBITMQ.getAmqpPort());
        props.put("RABBITMQ_USER", "guest");
        props.put("RABBITMQ_PASSWORD", "guest");
        props.put("REDIS_HOST", REDIS.getHost());
        props.put("REDIS_PORT", REDIS.getFirstMappedPort());
        props.put("spring.data.redis.host", REDIS.getHost());
        props.put("spring.data.redis.port", REDIS.getFirstMappedPort());
        props.put("spring.datasource.url", POSTGRES.getJdbcUrl() + "?currentSchema=public,identity,vendor,catalog,inventory,cart,order_ctx,payment,promotion,notification,review,shared");
        props.put("spring.datasource.username", POSTGRES.getUsername());
        props.put("spring.datasource.password", POSTGRES.getPassword());
        props.put("spring.rabbitmq.host", RABBITMQ.getHost());
        props.put("spring.rabbitmq.port", RABBITMQ.getAmqpPort());
        props.put("spring.rabbitmq.username", "guest");
        props.put("spring.rabbitmq.password", "guest");

        environment.getPropertySources().addFirst(new MapPropertySource("testcontainers-env", props));
    }
}
