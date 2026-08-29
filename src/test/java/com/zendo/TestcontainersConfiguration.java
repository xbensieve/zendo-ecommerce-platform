package com.zendo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers configuration for integration tests.
 *
 * <p>Provides a PostgreSQL container that Spring Boot auto-configures
 * as the datasource via {@link ServiceConnection}. The container is
 * shared across all tests that import this configuration within
 * the same JVM lifecycle.</p>
 *
 * <p>Required by: rules/10-TESTING.md §4, ADR-011</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("zendo_test")
                .withUsername("test")
                .withPassword("test");
    }
}
