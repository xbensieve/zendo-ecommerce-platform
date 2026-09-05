package com.zendo.security;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import com.zendo.security.application.TokenService;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ProductionActuatorExposureTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository userCredentialsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    private String createToken(Role role) {
        String email = "actuator_" + role.name().toLowerCase() + "_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Actuator", "Test");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                role
        );
        userCredentialsRepository.save(credentials);

        return tokenService.generateToken(user.getId().value(), List.of(role.name()), credentials.getSecurityVersion());
    }

    @Test
    @DisplayName("Health endpoint is public but does NOT leak details (show-details: never)")
    void healthEndpoint_doesNotLeakDetails() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).doesNotContain("database");
        assertThat(response.getBody()).doesNotContain("diskSpace");
        assertThat(response.getBody()).doesNotContain("zendo_db");
    }

    @Test
    @DisplayName("Prometheus metrics endpoint requires ADMIN authentication")
    void prometheusEndpoint_requiresAdminRole() {
        // Anonymous -> 401
        ResponseEntity<String> anonResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(anonResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Customer -> 403
        String customerToken = createToken(Role.CUSTOMER);
        HttpHeaders custHeaders = new HttpHeaders();
        custHeaders.setBearerAuth(customerToken);
        ResponseEntity<String> custResponse = restTemplate.exchange(
                "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(custHeaders), String.class
        );
        assertThat(custResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Admin -> 200
        String adminToken = createToken(Role.ADMIN);
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        ResponseEntity<String> adminResponse = restTemplate.exchange(
                "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class
        );
        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Dangerous actuator endpoints (/env, /beans, /heapdump) are not exposed")
    void dangerousActuators_areNotExposed() {
        String adminToken = createToken(Role.ADMIN);
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        // /actuator/env is NOT in management.endpoints.web.exposure.include
        ResponseEntity<String> envResp = restTemplate.exchange(
                "/actuator/env", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class
        );
        assertThat(envResp.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);

        // /actuator/heapdump is NOT in management.endpoints.web.exposure.include
        ResponseEntity<String> heapResp = restTemplate.exchange(
                "/actuator/heapdump", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class
        );
        assertThat(heapResp.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }
}
