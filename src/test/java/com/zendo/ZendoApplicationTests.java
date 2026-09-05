package com.zendo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test verifying that the Spring application context loads
 * successfully with all infrastructure dependencies (PostgreSQL via
 * Testcontainers, Flyway migrations).
 *
 * <p>This test validates:</p>
 * <ul>
 *   <li>Spring Boot context bootstraps without errors</li>
 *   <li>Flyway runs against a real PostgreSQL instance</li>
 *   <li>JPA entity scanning works (no entities yet, but wiring is valid)</li>
 *   <li>Datasource and connection pool initialize correctly</li>
 * </ul>
 */
@SpringBootTest
@org.springframework.boot.testcontainers.context.ImportTestcontainers(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ZendoApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the full application context starts successfully
        // with PostgreSQL (Testcontainers), Flyway, and JPA configured.
    }
}
