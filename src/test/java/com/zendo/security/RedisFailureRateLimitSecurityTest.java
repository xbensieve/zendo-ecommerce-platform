package com.zendo.security;

import com.zendo.shared.security.DistributedRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisFailureRateLimitSecurityTest {

    private StringRedisTemplate redisTemplate;
    private DistributedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        // Simulate Redis outage on every call
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new RedisConnectionFailureException("Connection refused to Redis:6379"));
        rateLimiter = new DistributedRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("Should FAIL_CLOSED for flash sale checkout when Redis is unreachable")
    void shouldFailClosedForFlashSaleWhenRedisDown() {
        String key = "rl:order:flashsale:user-123";
        // Even the very first request must be rejected under FAIL_CLOSED policy
        boolean allowed = rateLimiter.isAllowed(
                key, 3, 60, DistributedRateLimiter.RateLimitPolicy.FAIL_CLOSED
        );
        assertFalse(allowed, "Flash sale checkout must fail-closed during Redis outage to protect inventory");
    }

    @Test
    @DisplayName("Should enforce DEGRADE_LOCAL_STRICT for login/checkout when Redis is down")
    void shouldEnforceStrictLocalLimitWhenRedisDown() {
        String key = "rl:auth:login:192.168.1.10";
        // Under DEGRADE_LOCAL_STRICT, only a conservative limit (max 2) is permitted per node
        assertTrue(rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT));
        assertTrue(rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT));
        // 3rd attempt must be rejected even though maxRequests is 5
        assertFalse(rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT),
                "Strict degradation must cap local allowance to prevent multi-instance amplification attacks");
    }

    @Test
    @DisplayName("Should allow normal local degradation for non-critical public API")
    void shouldAllowNormalDegradationForPublicApi() {
        String key = "rl:api:192.168.1.20";
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL));
        }
        assertFalse(rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL));
    }
}
