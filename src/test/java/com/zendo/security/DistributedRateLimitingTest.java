package com.zendo.security;

import com.zendo.security.infrastructure.spring.RateLimiterFilter;
import com.zendo.shared.security.DistributedRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class DistributedRateLimitingTest {

    @Autowired
    private DistributedRateLimiter rateLimiter;

    @Test
    @DisplayName("Rate limiter allows requests within capacity and blocks excess requests")
    void rateLimiter_enforcesLimitPerKey() {
        String key = "rl:test:" + UUID.randomUUID();

        // Limit is 3 requests per 60 seconds
        assertThat(rateLimiter.isAllowed(key, 3, 60)).isTrue();
        assertThat(rateLimiter.isAllowed(key, 3, 60)).isTrue();
        assertThat(rateLimiter.isAllowed(key, 3, 60)).isTrue();

        // 4th request must be rejected
        assertThat(rateLimiter.isAllowed(key, 3, 60)).isFalse();

        // A different key is isolated and still allowed
        String differentKey = "rl:test:" + UUID.randomUUID();
        assertThat(rateLimiter.isAllowed(differentKey, 3, 60)).isTrue();
    }

    @Test
    @DisplayName("Local fallback allows rate limiting even when Redis is absent")
    void rateLimiter_localFallback_operatesWhenRedisAbsent() {
        DistributedRateLimiter fallbackLimiter = new DistributedRateLimiter(null);
        String key = "rl:local:" + UUID.randomUUID();

        assertThat(fallbackLimiter.isAllowed(key, 2, 60)).isTrue();
        assertThat(fallbackLimiter.isAllowed(key, 2, 60)).isTrue();
        assertThat(fallbackLimiter.isAllowed(key, 2, 60)).isFalse();
    }

    @Test
    @DisplayName("RateLimiterFilter intercepts and returns 429 when threshold is reached")
    void rateLimiterFilter_returns429_whenThresholdExceeded() throws ServletException, IOException {
        DistributedRateLimiter mockLimiter = mock(DistributedRateLimiter.class);
        RateLimiterFilter filter = new RateLimiterFilter(mockLimiter);
        ReflectionTestUtils.setField(filter, "enabled", true);

        // When login rate limit check fails
        when(mockLimiter.isAllowed(startsWith("rl:auth:login:"), eq(5), eq(60))).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Too many login attempts");
        verifyNoInteractions(chain);
    }
}
