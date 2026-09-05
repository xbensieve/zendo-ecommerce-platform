package com.zendo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zendo.catalog.api.CatalogQueryApi;
import com.zendo.notification.application.NotificationUseCases;
import com.zendo.notification.infrastructure.messaging.NotificationOrderEventConsumer;
import com.zendo.notification.infrastructure.messaging.NotificationPaymentEventConsumer;
import com.zendo.pricing.api.PricingQueryApi;
import com.zendo.pricing.application.PricingQueryApiImpl;
import com.zendo.promotion.api.PromotionQueryApi;
import com.zendo.security.api.SecurityCommandApi;
import com.zendo.security.application.SecurityCommandApiImpl;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import com.zendo.security.infrastructure.spring.RateLimiterFilter;
import com.zendo.shared.messaging.DomainEvent;
import com.zendo.shared.messaging.EventPublisher;
import com.zendo.shared.messaging.EventSigner;
import com.zendo.shared.outbox.OutboxEvent;
import com.zendo.shared.outbox.OutboxEventPublisher;
import com.zendo.shared.outbox.OutboxEventStore;
import com.zendo.shared.security.DistributedRateLimiter;
import com.zendo.vendor.application.VendorUseCases;
import com.zendo.vendor.domain.Vendor;
import com.zendo.vendor.domain.VendorId;
import com.zendo.vendor.domain.VendorName;
import com.zendo.vendor.domain.VendorRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SecurityHardeningPhase2Test {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EventSigner eventSigner = new EventSigner("phase2-test-secret-key-32-chars-minimum!");

    // --- 1. Zero-Dollar Order / Price Floor Tests ---
    @Test
    @DisplayName("PricingQueryApiImpl must clamp unit price to minimum 0.01 to prevent zero-amount deadlock")
    void pricingQueryApi_clampsToMinimumFloor() {
        CatalogQueryApi catalogQueryApi = mock(CatalogQueryApi.class);
        PromotionQueryApi promotionQueryApi = mock(PromotionQueryApi.class);

        when(catalogQueryApi.getVariantInfo(anyString(), anyString())).thenReturn(
                Optional.of(new CatalogQueryApi.ProductVariantInfo("prod-1", "vendor-1", "SKU1", "Item", new BigDecimal("50.00"), "USD"))
        );
        // 100% discount
        when(promotionQueryApi.getApplicablePromotions(anyString(), anyString(), anyString())).thenReturn(
                List.of(new PromotionQueryApi.PromotionDetails("PROMO100", "PERCENTAGE", new BigDecimal("100")))
        );

        PricingQueryApi pricingApi = new PricingQueryApiImpl(catalogQueryApi, promotionQueryApi);
        PricingQueryApi.PricedItem item = pricingApi.calculateItemPrice("vendor-1", "prod-1", "SKU1", 1, "FREE");

        assertThat(item.finalUnitPrice()).isEqualByComparingTo("0.01");
        assertThat(item.appliedDiscount()).isEqualByComparingTo("49.99");
    }

    // --- 2. Vendor Role Elevation & Admin Role Management ---
    @Test
    @DisplayName("Vendor activation should elevate owner role from CUSTOMER to VENDOR")
    void vendorActivation_elevatesCustomerToVendor() {
        VendorRepository vendorRepository = mock(VendorRepository.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        SecurityCommandApi securityCommandApi = mock(SecurityCommandApi.class);

        String ownerId = "user-123";
        Vendor vendor = Vendor.onboard(new VendorName("Acme Shop"), ownerId);
        when(vendorRepository.findById(any())).thenReturn(Optional.of(vendor));

        VendorUseCases vendorUseCases = new VendorUseCases(vendorRepository, eventPublisher, securityCommandApi);
        vendorUseCases.activateVendor(vendor.getId().value().toString());

        verify(securityCommandApi).upgradeToVendorIfCustomer(ownerId);
    }

    @Test
    @DisplayName("SecurityCommandApi upgrades CUSTOMER to VENDOR but preserves ADMIN")
    void securityCommandApi_upgradesCustomerAndPreservesAdmin() {
        UserCredentialsRepository credsRepo = mock(UserCredentialsRepository.class);
        SecurityCommandApi securityCommandApi = new SecurityCommandApiImpl(credsRepo);

        // Scenario A: Customer upgraded to VENDOR
        UserCredentials customerCreds = new UserCredentials("cust-1", "hash", Role.CUSTOMER, 1);
        when(credsRepo.findByUserId("cust-1")).thenReturn(Optional.of(customerCreds));

        securityCommandApi.upgradeToVendorIfCustomer("cust-1");

        ArgumentCaptor<UserCredentials> captor = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credsRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.VENDOR);

        // Scenario B: Admin remains ADMIN
        reset(credsRepo);
        UserCredentials adminCreds = new UserCredentials("admin-1", "hash", Role.ADMIN, 2);
        when(credsRepo.findByUserId("admin-1")).thenReturn(Optional.of(adminCreds));

        securityCommandApi.upgradeToVendorIfCustomer("admin-1");
        verify(credsRepo, never()).save(any());
    }

    @Test
    @DisplayName("Admin updateUserRole assigns valid roles and rejects invalid inputs")
    void securityCommandApi_adminRoleAssignment() {
        UserCredentialsRepository credsRepo = mock(UserCredentialsRepository.class);
        SecurityCommandApi securityCommandApi = new SecurityCommandApiImpl(credsRepo);

        UserCredentials userCreds = new UserCredentials("user-5", "hash", Role.CUSTOMER, 1);
        when(credsRepo.findByUserId("user-5")).thenReturn(Optional.of(userCreds));

        securityCommandApi.updateUserRole("user-5", "ADMIN");

        ArgumentCaptor<UserCredentials> captor = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credsRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getSecurityVersion()).isEqualTo(2);

        // Invalid role
        assertThatThrownBy(() -> securityCommandApi.updateUserRole("user-5", "SUPER_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role");
    }

    // --- 3. RateLimiterFilter URI Normalization ---
    @Test
    @DisplayName("RateLimiterFilter normalizes URI with trailing slashes and uppercase characters")
    void rateLimiterFilter_normalizesUri() throws Exception {
        DistributedRateLimiter rateLimiter = mock(DistributedRateLimiter.class);
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyInt(), any())).thenReturn(true);

        RateLimiterFilter filter = new RateLimiterFilter(rateLimiter);
        FilterChain filterChain = mock(FilterChain.class);

        // Case 1: Trailing slash on login
        MockHttpServletRequest requestWithSlash = new MockHttpServletRequest("POST", "/api/v1/auth/login/");
        requestWithSlash.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse responseWithSlash = new MockHttpServletResponse();

        filter.doFilter(requestWithSlash, responseWithSlash, filterChain);

        // Verifies the auth-login key with strict limit (5) was used, NOT the generic 300 limit
        verify(rateLimiter).isAllowed(eq("rl:auth:login:192.168.1.100"), eq(5), eq(60), eq(DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT));

        // Case 2: Uppercase URI
        reset(rateLimiter);
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyInt(), any())).thenReturn(true);

        MockHttpServletRequest requestUpper = new MockHttpServletRequest("POST", "/API/V1/AUTH/LOGIN");
        requestUpper.setRemoteAddr("192.168.1.101");
        MockHttpServletResponse responseUpper = new MockHttpServletResponse();

        filter.doFilter(requestUpper, responseUpper, filterChain);

        verify(rateLimiter).isAllowed(eq("rl:auth:login:192.168.1.101"), eq(5), eq(60), eq(DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT));
    }

    // --- 4. Notification Event Freshness Tests ---
    @Test
    @DisplayName("NotificationOrderEventConsumer strictly validates occurredAt timestamp")
    void notificationOrderEventConsumer_validatesFreshness() throws Exception {
        NotificationUseCases notificationUseCases = mock(NotificationUseCases.class);
        NotificationOrderEventConsumer consumer = new NotificationOrderEventConsumer(notificationUseCases, objectMapper, eventSigner);

        // A. Missing occurredAt -> REJECT
        Map<String, Object> eventWithoutTimestamp = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "orderId", UUID.randomUUID().toString(),
                "customerId", "cust-1",
                "totalAmount", "100.00"
        );
        String payloadWithoutTimestamp = objectMapper.writeValueAsString(eventWithoutTimestamp);
        String sig1 = eventSigner.sign(payloadWithoutTimestamp);

        assertThatThrownBy(() -> consumer.handle(payloadWithoutTimestamp, sig1))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("occurredAt");

        // B. Stale occurredAt (> 24h) -> REJECT
        Map<String, Object> staleEvent = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "orderId", UUID.randomUUID().toString(),
                "customerId", "cust-1",
                "totalAmount", "100.00",
                "occurredAt", Instant.now().minus(25, ChronoUnit.HOURS).toString()
        );
        String payloadStale = objectMapper.writeValueAsString(staleEvent);
        String sig2 = eventSigner.sign(payloadStale);

        assertThatThrownBy(() -> consumer.handle(payloadStale, sig2))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        // C. Fresh occurredAt -> ACCEPT
        Map<String, Object> freshEvent = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "orderId", UUID.randomUUID().toString(),
                "customerId", "cust-1",
                "totalAmount", "100.00",
                "occurredAt", Instant.now().toString()
        );
        String payloadFresh = objectMapper.writeValueAsString(freshEvent);
        String sig3 = eventSigner.sign(payloadFresh);

        consumer.handle(payloadFresh, sig3);
        verify(notificationUseCases).createAndSendNotification(eq("cust-1"), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("NotificationPaymentEventConsumer strictly validates occurredAt timestamp")
    void notificationPaymentEventConsumer_validatesFreshness() throws Exception {
        NotificationUseCases notificationUseCases = mock(NotificationUseCases.class);
        NotificationPaymentEventConsumer consumer = new NotificationPaymentEventConsumer(notificationUseCases, objectMapper, eventSigner);

        // Stale payment event
        Map<String, Object> stalePayment = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PaymentAuthorized",
                "orderId", UUID.randomUUID().toString(),
                "customerId", "cust-1",
                "amount", "100.00",
                "currency", "USD",
                "occurredAt", Instant.now().minus(48, ChronoUnit.HOURS).toString()
        );
        String payloadStale = objectMapper.writeValueAsString(stalePayment);
        String sig = eventSigner.sign(payloadStale);

        assertThatThrownBy(() -> consumer.handle(payloadStale, sig))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    // --- 5. Outbox Serialization Test ---
    @Test
    @DisplayName("OutboxEventPublisher serializes actual event payload and aggregate type")
    void outboxEventPublisher_serializesRealData() {
        OutboxEventStore store = mock(OutboxEventStore.class);
        OutboxEventPublisher publisher = new OutboxEventPublisher(store, objectMapper);

        record DummyEvent(UUID eventId, Instant occurredOn, String aggregateId, String eventType, String message) implements DomainEvent {
            @Override public UUID getEventId() { return eventId; }
            @Override public Instant getOccurredOn() { return occurredOn; }
            @Override public String getAggregateId() { return aggregateId; }
            @Override public String getEventType() { return eventType; }
        }

        UUID evtId = UUID.randomUUID();
        Instant now = Instant.now();
        DummyEvent event = new DummyEvent(evtId, now, "agg-123", "DummyEvent", "Real payload content");

        publisher.publish(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(store).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.payload()).contains("Real payload content");
        assertThat(saved.payload()).contains(evtId.toString());
        assertThat(saved.aggregateType()).isEqualTo("DummyEvent");
    }
}
