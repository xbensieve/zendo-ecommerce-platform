package com.zendo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSecretConfigurationTest {

    @Test
    @DisplayName("Verify application-prod.yml contains no insecure default fallbacks for critical secrets")
    void applicationProdYml_mustNotHaveDefaultSecrets() throws Exception {
        Path prodConfigPath = Path.of("src/main/resources/application-prod.yml");
        assertTrue(Files.exists(prodConfigPath), "application-prod.yml must exist");

        String content = Files.readString(prodConfigPath);

        // Verify secrets are strictly injected from env without default fallback values
        assertTrue(content.contains("${JWT_SECRET}"), "JWT_SECRET must be required via env");
        assertFalse(content.matches("(?s).*\\$\\{JWT_SECRET:[^}]+}.*"), "JWT_SECRET must not have default fallback");

        assertTrue(content.contains("${EVENT_SIGNING_SECRET}"), "EVENT_SIGNING_SECRET must be required via env");
        assertFalse(content.matches("(?s).*\\$\\{EVENT_SIGNING_SECRET:[^}]+}.*"), "EVENT_SIGNING_SECRET must not have default fallback");

        assertTrue(content.contains("${DB_PASSWORD}"), "DB_PASSWORD must be required via env");
        assertFalse(content.matches("(?s).*\\$\\{DB_PASSWORD:[^}]+}.*"), "DB_PASSWORD must not have default fallback");

        assertTrue(content.contains("${RABBITMQ_PASSWORD}"), "RABBITMQ_PASSWORD must be required via env");
        assertFalse(content.matches("(?s).*\\$\\{RABBITMQ_PASSWORD:[^}]+}.*"), "RABBITMQ_PASSWORD must not have default fallback");

        assertTrue(content.contains("${REDIS_PASSWORD}"), "REDIS_PASSWORD must be required via env");
        assertFalse(content.matches("(?s).*\\$\\{REDIS_PASSWORD:[^}]+}.*"), "REDIS_PASSWORD must not have default fallback");

        // Verify swagger is disabled in production
        assertTrue(content.contains("springdoc:"), "springdoc must be configured in prod");
        assertTrue(content.contains("api-docs:\n    enabled: false") || content.contains("api-docs:\r\n    enabled: false"), "api-docs must be false in prod");
        assertTrue(content.contains("swagger-ui:\n    enabled: false") || content.contains("swagger-ui:\r\n    enabled: false"), "swagger-ui must be false in prod");
    }

    @Test
    @DisplayName("Verify docker-compose.prod.yml declares EVENT_SIGNING_SECRET and REDIS_PASSWORD")
    void dockerComposeProd_mustDeclareEventSigningSecret() throws Exception {
        Path dockerProdPath = Path.of("docker-compose.prod.yml");
        assertTrue(Files.exists(dockerProdPath), "docker-compose.prod.yml must exist");

        String content = Files.readString(dockerProdPath);
        assertTrue(content.contains("EVENT_SIGNING_SECRET=${EVENT_SIGNING_SECRET}"),
                "docker-compose.prod.yml must declare EVENT_SIGNING_SECRET");
        assertTrue(content.contains("REDIS_PASSWORD=${REDIS_PASSWORD}"),
                "docker-compose.prod.yml must declare REDIS_PASSWORD");
    }
}
