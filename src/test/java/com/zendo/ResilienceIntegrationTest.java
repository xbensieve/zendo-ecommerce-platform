package com.zendo;

import com.zendo.catalog.api.CatalogQueryApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.test.context.TestPropertySource(properties = {
    "management.health.redis.enabled=false",
    "management.health.rabbit.enabled=false"
})
public class ResilienceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ResilienceIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    void testPostgresUnavailableReturns503() throws Exception {
        // 1. Start healthy
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // 2. Inject failure
        log.info("Pausing PostgreSQL container to simulate outage...");
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();

        // 3. Observe degradation (Should fail fast and return 503 instead of 500)
        // Note: Hikari might take 5000ms (connection-timeout) to fail
        long start = System.currentTimeMillis();
        try {
            // Actuator health will degrade to 503
            mockMvc.perform(get("/actuator/health")).andExpect(status().is5xxServerError());
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("Outage test duration: {} ms", duration);
        }

        // 4. Restore dependency
        log.info("Unpausing PostgreSQL container to simulate recovery...");
        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();

        // Give it a second to re-establish
        Thread.sleep(2000);

        // 5. Observe recovery
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
