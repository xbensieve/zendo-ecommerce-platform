package com.zendo.review;

import com.zendo.order.api.OrderQueryApi;
import com.zendo.review.api.rest.ReviewController.CreateReviewRequest;
import com.zendo.security.application.AuthUseCases;
import com.zendo.security.application.RegistrationUseCases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class ReviewIntegrationTest {

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
    private RegistrationUseCases registrationUseCases;

    @Autowired
    private AuthUseCases authUseCases;

    // We mock OrderQueryApi since we don't want to create full orders for these tests
    @MockBean
    private OrderQueryApi orderQueryApi;

    private String customerToken;
    private String customerId;

    @BeforeEach
    void setup() {
        // Create Customer
        String custEmail = "customer_" + UUID.randomUUID() + "@example.com";
        var custResult = registrationUseCases.register(custEmail, "SecurePass123!", "Customer", "User");
        customerId = custResult.userId();
        customerToken = authUseCases.login(custEmail, "SecurePass123!").token();
    }

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void shouldCreateReviewForEligiblePurchase() {
        UUID orderItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        
        when(orderQueryApi.isEligibleForReview(eq(customerId), eq(orderItemId), eq(productId))).thenReturn(true);

        CreateReviewRequest request = new CreateReviewRequest(orderItemId, productId, 5, "Great product!");
        HttpEntity<CreateReviewRequest> entity = new HttpEntity<>(request, getHeaders(customerToken));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/reviews", entity, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @Test
    void shouldRejectReviewForIneligiblePurchase() {
        UUID orderItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        
        // Mock false
        when(orderQueryApi.isEligibleForReview(eq(customerId), eq(orderItemId), eq(productId))).thenReturn(false);

        CreateReviewRequest request = new CreateReviewRequest(orderItemId, productId, 5, "Great product!");
        HttpEntity<CreateReviewRequest> entity = new HttpEntity<>(request, getHeaders(customerToken));

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/reviews", entity, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectDuplicateReviewSafelyUnderConcurrency() throws Exception {
        UUID orderItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        
        when(orderQueryApi.isEligibleForReview(eq(customerId), eq(orderItemId), eq(productId))).thenReturn(true);

        CreateReviewRequest request = new CreateReviewRequest(orderItemId, productId, 5, "Great product!");
        HttpEntity<CreateReviewRequest> entity = new HttpEntity<>(request, getHeaders(customerToken));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> restTemplate.postForEntity("/api/v1/reviews", entity, String.class));
        }

        List<Future<ResponseEntity<String>>> results = executor.invokeAll(tasks);
        
        int successCount = 0;
        int conflictCount = 0;

        for (var result : results) {
            ResponseEntity<String> res = result.get();
            if (res.getStatusCode() == HttpStatus.OK) {
                successCount++;
            } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                conflictCount++;
            }
        }
        
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threadCount - 1);
        
        // Check rating summary
        ResponseEntity<Map> summaryRes = restTemplate.getForEntity("/api/v1/products/" + productId + "/reviews/summary", Map.class);
        assertThat(summaryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = summaryRes.getBody();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("totalReviews")).isEqualTo(1);
    }

    @Test
    void shouldRejectUnauthenticatedRequest() {
        CreateReviewRequest request = new CreateReviewRequest(UUID.randomUUID(), UUID.randomUUID(), 5, "Great");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/reviews", request, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
