package com.zendo.security;

import com.zendo.identity.domain.UserRepository;
import com.zendo.security.domain.UserCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class RegistrationIntegrationTest {

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
    private UserCredentialsRepository credentialsRepository;

    @BeforeEach
    void setup() {
        // We use unique emails per test to avoid state collision
    }

    @Test
    void shouldRegisterSuccessfullyAndLogin() {
        String email = "newuser_" + UUID.randomUUID() + "@example.com";
        Map<String, String> request = Map.of(
                "email", email,
                "password", "securePassword123!",
                "firstName", "John",
                "lastName", "Doe"
        );

        // 1. Register
        ResponseEntity<Map> regResponse = restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        
        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = regResponse.getBody();
        assertThat(body).isNotNull();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data).isNotNull();
        
        String userId = (String) data.get("userId");
        assertThat(userId).isNotBlank();
        assertThat(data.get("email")).isEqualTo(email);
        assertThat(data.get("role")).isEqualTo("CUSTOMER");

        // Verify Data presence
        assertThat(userRepository.findByEmail(email)).isPresent();
        assertThat(credentialsRepository.findByUserId(userId)).isPresent();

        // 2. Login
        Map<String, String> loginRequest = Map.of("email", email, "password", "securePassword123!");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity("/api/v1/auth/login", loginRequest, Map.class);
        
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> loginData = (Map<?, ?>) loginResponse.getBody().get("data");
        assertThat(loginData.get("token")).isNotNull();
        assertThat(loginData.get("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldRejectDuplicateRegistration() {
        String email = "dup_" + UUID.randomUUID() + "@example.com";
        Map<String, String> request = Map.of(
                "email", email,
                "password", "securePassword123!",
                "firstName", "John",
                "lastName", "Doe"
        );

        // First registration
        ResponseEntity<Map> res1 = restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second registration
        ResponseEntity<String> res2 = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody()).contains("already exists");
    }

    @Test
    void shouldRejectInvalidInput() {
        Map<String, String> request = Map.of(
                "email", "",
                "password", "securePassword123!",
                "firstName", "John",
                "lastName", "Doe"
        );

        ResponseEntity<String> res = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldPreventRoleEscalationInRegistration() {
        String email = "hacker_" + UUID.randomUUID() + "@example.com";
        Map<String, String> request = Map.of(
                "email", email,
                "password", "securePassword123!",
                "firstName", "John",
                "lastName", "Doe",
                "role", "ADMIN" // Attempt to inject admin role
        );

        ResponseEntity<Map> regResponse = restTemplate.postForEntity("/api/v1/auth/register", request, Map.class);
        
        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) regResponse.getBody().get("data");
        // The server must ignore the injected role and assign CUSTOMER
        assertThat(data.get("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldHandleConcurrentRegistrationSafely() throws InterruptedException, ExecutionException {
        int threadCount = 5;
        String email = "concurrent_" + UUID.randomUUID() + "@example.com";
        Map<String, String> request = Map.of(
                "email", email,
                "password", "securePassword123!",
                "firstName", "Concurrent",
                "lastName", "User"
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> restTemplate.postForEntity("/api/v1/auth/register", request, String.class));
        }

        List<Future<ResponseEntity<String>>> results = executor.invokeAll(tasks);
        
        int successCount = 0;
        int conflictCount = 0;

        for (Future<ResponseEntity<String>> result : results) {
            ResponseEntity<String> res = result.get();
            if (res.getStatusCode() == HttpStatus.OK) {
                successCount++;
            } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                conflictCount++;
            }
        }

        executor.shutdown();

        // Exactly 1 success, the rest conflict
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threadCount - 1);

        // Verify no partial state
        long userCount = userRepository.findByEmail(email).stream().count();
        assertThat(userCount).isEqualTo(1);
    }

    @Test
    void shouldRejectPasswordShorterThanTenCharacters() {
        Map<String, String> request = Map.of(
                "email", "shortpass_" + UUID.randomUUID() + "@example.com",
                "password", "Short9!",
                "firstName", "John",
                "lastName", "Doe"
        );

        ResponseEntity<String> res = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectCommonLeakedPassword() {
        Map<String, String> request = Map.of(
                "email", "commonpass_" + UUID.randomUUID() + "@example.com",
                "password", "password1234",
                "firstName", "John",
                "lastName", "Doe"
        );

        ResponseEntity<String> res = restTemplate.postForEntity("/api/v1/auth/register", request, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("easily guessable");
    }
}
