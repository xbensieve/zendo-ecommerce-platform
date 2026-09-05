package com.zendo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "springdoc.swagger-ui.enabled=false",
        "springdoc.api-docs.enabled=false",
        "zendo.security.event.signing-secret=production-test-signing-secret-32-chars-ok!!"
})
public class ProductionSwaggerExposureTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Production Lockdown: /v3/api-docs must NOT be accessible when Swagger is disabled")
    void apiDocs_mustNotBeAccessibleInProduction() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Production Lockdown: /swagger-ui.html must NOT be accessible when Swagger is disabled")
    void swaggerUiHtml_mustNotBeAccessibleInProduction() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Production Lockdown: /swagger-ui/index.html must NOT be accessible when Swagger is disabled")
    void swaggerUiIndex_mustNotBeAccessibleInProduction() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
    }
}
