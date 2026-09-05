package com.zendo.security;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserId;
import com.zendo.identity.domain.UserRepository;
import com.zendo.identity.domain.UserStatus;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository userCredentialsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // We will create data in each test uniquely to avoid collisions.
    }

    @Test
    void shouldLoginSuccessfullyAndAccessProtectedEndpoint() {
        // Arrange
        String email = "customer_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Test", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("password123"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        // Act - Login
        Map<String, String> loginRequest = Map.of("email", email, "password", "password123");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginRequest, Map.class);

        // Assert - Login
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = loginResponse.getBody();
        assertThat(body).isNotNull();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data).isNotNull();
        String token = (String) data.get("token");
        assertThat(token).isNotBlank();

        // Act - Access Protected Endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> cartResponse = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, entity, String.class);

        // Assert - Access Protected
        assertThat(cartResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        // Arrange
        String email = "customer_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Test", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("password123"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        // Act - Login
        Map<String, String> loginRequest = Map.of("email", email, "password", "wrongpass");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginRequest, String.class);

        // Assert
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectLoginForSuspendedUser() {
        // Arrange
        String email = "suspended_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Suspended", "User");
        user.suspend();
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("password123"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        // Act - Login
        Map<String, String> loginRequest = Map.of("email", email, "password", "password123");
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginRequest, String.class);

        // Assert
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectAccessWithoutToken() {
        ResponseEntity<String> cartResponse = restTemplate.getForEntity("/api/v1/carts/me", String.class);
        assertThat(cartResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
