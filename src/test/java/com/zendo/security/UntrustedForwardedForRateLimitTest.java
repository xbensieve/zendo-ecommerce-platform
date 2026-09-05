package com.zendo.security;

import com.zendo.security.infrastructure.spring.RateLimiterFilter;
import com.zendo.shared.security.DistributedRateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UntrustedForwardedForRateLimitTest {

    private DistributedRateLimiter rateLimiter;
    private RateLimiterFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter = mock(DistributedRateLimiter.class);
        filter = new RateLimiterFilter(rateLimiter);

        // Ensure filter is enabled
        Field enabledField = RateLimiterFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(filter, true);
    }

    @Test
    @DisplayName("Attacker rotating X-Forwarded-For headers must NOT bypass remoteAddr rate limiting")
    void spoofedXForwardedFor_mustNotBypassRateLimiter() throws Exception {
        String clientSocketIp = "192.168.1.100";
        String expectedKey = "rl:auth:login:" + clientSocketIp;

        // When 5 requests are exhausted, rate limiter returns false
        when(rateLimiter.isAllowed(eq(expectedKey), eq(5), eq(60), any()))
                .thenReturn(true, true, true, true, true, false);

        FilterChain chain = mock(FilterChain.class);

        // Send 5 requests with spoofed X-Forwarded-For headers
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr(clientSocketIp);
            request.addHeader("X-Forwarded-For", "203.0.113." + i); // Attacker tries to spoof client IP
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }

        // 6th request with yet another spoofed IP
        MockHttpServletRequest request6 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request6.setRemoteAddr(clientSocketIp);
        request6.addHeader("X-Forwarded-For", "198.51.100.99");
        MockHttpServletResponse response6 = new MockHttpServletResponse();

        filter.doFilter(request6, response6, chain);

        // Must be rejected with 429 Too Many Requests because remoteAddr is keyed, ignoring spoofed header
        assertEquals(429, response6.getStatus(), "Spoofed X-Forwarded-For must not bypass rate limit on remoteAddr");
        verify(rateLimiter, times(6)).isAllowed(eq(expectedKey), eq(5), eq(60), any());
    }
}
