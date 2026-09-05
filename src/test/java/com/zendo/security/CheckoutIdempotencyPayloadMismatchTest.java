package com.zendo.security;

import com.zendo.order.domain.OrderException;
import com.zendo.order.domain.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
public class CheckoutIdempotencyPayloadMismatchTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Should detect idempotency payload mismatch when key is replayed with different body")
    void shouldDetectPayloadMismatchOnReplay() {
        String key = "test:idempotency:" + UUID.randomUUID();
        String originalPayloadHash = "hash_payload_100_usd_coupon_a";
        String alteredPayloadHash = "hash_payload_500_usd_coupon_b";

        // 1. First request with original payload hash -> accepted
        boolean firstAttempt = orderRepository.checkAndSaveIdempotencyKey(key, originalPayloadHash);
        assertTrue(firstAttempt, "First attempt with new key must succeed");

        // 2. Exact replay with same payload hash -> recognized as duplicate replay (returns false)
        boolean duplicateAttempt = orderRepository.checkAndSaveIdempotencyKey(key, originalPayloadHash);
        assertFalse(duplicateAttempt, "Exact replay with matching payload hash must return false (duplicate)");

        // 3. Attacker replays key with materially different payload -> must throw OrderException (payload mismatch)
        OrderException ex = assertThrows(
                OrderException.class,
                () -> orderRepository.checkAndSaveIdempotencyKey(key, alteredPayloadHash),
                "Replaying same key with different payload must throw OrderException"
        );
        assertTrue(ex.getMessage().contains("payload mismatch"), "Error message must indicate payload mismatch");
    }
}
