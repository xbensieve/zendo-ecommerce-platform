package com.zendo.security;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import com.zendo.order.domain.*;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CrossCustomerOrderAttackTest {

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

    @Autowired
    private OrderRepository orderRepository;

    private record UserContext(String userId, String token) {}

    private UserContext createUserAndToken(String emailPrefix, Role role) {
        String email = emailPrefix + "_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Test", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                role
        );
        userCredentialsRepository.save(credentials);

        String token = tokenService.generateToken(user.getId().value(), List.of(role.name()), credentials.getSecurityVersion());
        return new UserContext(user.getId().value(), token);
    }

    private ParentOrder createOrderForCustomer(String customerId) {
        UUID orderId = UUID.randomUUID();
        UUID vendorId = UUID.randomUUID();
        OrderItem item = new OrderItem(
                UUID.randomUUID(), UUID.randomUUID(), "SKU-TEST", "Product Test",
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), null, 1
        );
        ChildOrder childOrder = new ChildOrder(UUID.randomUUID(), vendorId, "USD", List.of(item));
        ParentOrder order = new ParentOrder(orderId, customerId, OrderStatus.PAYMENT_PENDING, "USD", List.of(childOrder), new BigDecimal("100.00"));
        orderRepository.save(order);
        return order;
    }

    @Test
    @DisplayName("BOLA Attack: Customer B must NOT be able to view Customer A's order (HTTP 403)")
    void customerB_cannotViewCustomerA_Order() {
        UserContext customerA = createUserAndToken("customer_a", Role.CUSTOMER);
        UserContext customerB = createUserAndToken("customer_b", Role.CUSTOMER);

        ParentOrder orderA = createOrderForCustomer(customerA.userId());

        // Customer A requests own order -> 200 OK
        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(customerA.token());
        ResponseEntity<String> responseA = restTemplate.exchange(
                "/api/v1/orders/" + orderA.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                String.class
        );
        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Customer B requests Customer A's order -> 403 Forbidden
        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(customerB.token());
        ResponseEntity<String> responseB = restTemplate.exchange(
                "/api/v1/orders/" + orderA.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                String.class
        );
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Admin can view Customer A's order (HTTP 200)")
    void admin_canViewCustomerA_Order() {
        UserContext customerA = createUserAndToken("customer_a2", Role.CUSTOMER);
        UserContext admin = createUserAndToken("admin_auditor", Role.ADMIN);

        ParentOrder orderA = createOrderForCustomer(customerA.userId());

        HttpHeaders headersAdmin = new HttpHeaders();
        headersAdmin.setBearerAuth(admin.token());
        ResponseEntity<String> responseAdmin = restTemplate.exchange(
                "/api/v1/orders/" + orderA.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersAdmin),
                String.class
        );
        assertThat(responseAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
