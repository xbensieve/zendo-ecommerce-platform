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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ReverseProxyRateLimitingTest {

    private DistributedRateLimiter rateLimiter;
    private RateLimiterFilter filter;

    private static final String TRUSTED_PROXY_IP = "10.0.0.1";
    private static final String UNTRUSTED_CLIENT_IP = "203.0.113.199";

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter = mock(DistributedRateLimiter.class);
        filter = new RateLimiterFilter(rateLimiter);

        // Configure trusted proxies (10.0.0.0/8, 127.0.0.1)
        filter.setTrustedProxies("127.0.0.1,::1,10.0.0.0/8");

        // Enable filter
        Field enabledField = RateLimiterFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(filter, true);
    }

    @Test
    @DisplayName("P2-01: Direct untrusted client spoofing X-Forwarded-For must be ignored and remoteAddr keyed")
    void untrustedClientSpoofingXForwardedFor_isIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(UNTRUSTED_CLIENT_IP);
        request.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1"); // Attempted spoof
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiter.isAllowed(eq("rl:auth:login:" + UNTRUSTED_CLIENT_IP), eq(5), eq(60), any()))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        // Verify key uses UNTRUSTED_CLIENT_IP, NOT the spoofed header IP
        verify(rateLimiter).isAllowed(eq("rl:auth:login:" + UNTRUSTED_CLIENT_IP), eq(5), eq(60), any());
        verify(rateLimiter, never()).isAllowed(eq("rl:auth:login:8.8.8.8"), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("P2-01: Trusted proxy forwarded client IP must be correctly resolved")
    void trustedProxyForwardedClientIp_isCorrectlyResolved() throws Exception {
        String realClientIp = "198.51.100.50";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(TRUSTED_PROXY_IP); // Connection comes from trusted proxy
        request.addHeader("X-Forwarded-For", realClientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiter.isAllowed(eq("rl:auth:login:" + realClientIp), eq(5), eq(60), any()))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(rateLimiter).isAllowed(eq("rl:auth:login:" + realClientIp), eq(5), eq(60), any());
    }

    @Test
    @DisplayName("P2-01: Two distinct clients behind the same proxy are isolated and do not collide")
    void twoRealClientsBehindSameProxy_areIsolatedWithoutCollision() throws Exception {
        String clientA_Ip = "198.51.100.10";
        String clientB_Ip = "198.51.100.20";

        FilterChain chain = mock(FilterChain.class);

        // Client A sends 5 login requests and exhausts limit
        when(rateLimiter.isAllowed(eq("rl:auth:login:" + clientA_Ip), eq(5), eq(60), any()))
                .thenReturn(true, true, true, true, true, false);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest reqA = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            reqA.setRemoteAddr(TRUSTED_PROXY_IP);
            reqA.addHeader("X-Forwarded-For", clientA_Ip);
            MockHttpServletResponse respA = new MockHttpServletResponse();
            filter.doFilter(reqA, respA, chain);
            assertEquals(200, respA.getStatus());
        }

        // 6th request by Client A gets 429
        MockHttpServletRequest reqA_Blocked = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        reqA_Blocked.setRemoteAddr(TRUSTED_PROXY_IP);
        reqA_Blocked.addHeader("X-Forwarded-For", clientA_Ip);
        MockHttpServletResponse respA_Blocked = new MockHttpServletResponse();
        filter.doFilter(reqA_Blocked, respA_Blocked, chain);
        assertEquals(429, respA_Blocked.getStatus(), "Client A must be rate-limited after 5 attempts");

        // Client B behind the SAME proxy must NOT be affected!
        when(rateLimiter.isAllowed(eq("rl:auth:login:" + clientB_Ip), eq(5), eq(60), any()))
                .thenReturn(true);

        MockHttpServletRequest reqB = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        reqB.setRemoteAddr(TRUSTED_PROXY_IP);
        reqB.addHeader("X-Forwarded-For", clientB_Ip);
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilter(reqB, respB, chain);

        assertEquals(200, respB.getStatus(), "Client B must NOT be blocked by Client A's rate limit exhaustion");
        verify(rateLimiter).isAllowed(eq("rl:auth:login:" + clientB_Ip), eq(5), eq(60), any());
    }

    @Test
    @DisplayName("P2-01: Trusted proxy chain with multiple proxies correctly resolves the first untrusted client IP")
    void trustedProxyChain_skipsIntermediateTrustedProxies() throws Exception {
        String genuineClient = "203.0.113.77";
        // Intermediate proxy is also in 10.0.0.0/8 (10.0.0.2)
        String xffHeader = genuineClient + ", 10.0.0.2";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(TRUSTED_PROXY_IP); // 10.0.0.1
        request.addHeader("X-Forwarded-For", xffHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(rateLimiter.isAllowed(eq("rl:auth:login:" + genuineClient), eq(5), eq(60), any()))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(rateLimiter).isAllowed(eq("rl:auth:login:" + genuineClient), eq(5), eq(60), any());
    }

    @Test
    @DisplayName("P2-01: Redis outage causes fail-closed on flash-sale endpoints")
    void redisOutage_flashSale_failsClosed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/flash-sale");
        request.setRemoteAddr(UNTRUSTED_CLIENT_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When Redis is down, DistributedRateLimiter with FAIL_CLOSED policy returns false
        when(rateLimiter.isAllowed(anyString(), eq(3), eq(60), eq(DistributedRateLimiter.RateLimitPolicy.FAIL_CLOSED)))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("P2-01: Redis outage applies strict local degradation on login endpoints")
    void redisOutage_login_appliesStrictLocalDegradation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(UNTRUSTED_CLIENT_IP);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // DEGRADE_LOCAL_STRICT policy is passed to rateLimiter
        when(rateLimiter.isAllowed(eq("rl:auth:login:" + UNTRUSTED_CLIENT_IP), eq(5), eq(60), eq(DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT)))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(rateLimiter).isAllowed(eq("rl:auth:login:" + UNTRUSTED_CLIENT_IP), eq(5), eq(60), eq(DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT));
        verify(chain).doFilter(request, response);
    }
}
