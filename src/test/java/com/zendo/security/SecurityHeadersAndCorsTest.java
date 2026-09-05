package com.zendo.security;

import com.zendo.security.infrastructure.spring.JwtAuthenticationFilter;
import com.zendo.security.infrastructure.spring.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class SecurityHeadersAndCorsTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthFilter;

    @Mock
    private com.zendo.security.infrastructure.spring.RateLimiterFilter rateLimiterFilter;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtAuthFilter, rateLimiterFilter);
        ReflectionTestUtils.setField(securityConfig, "allowedOriginsConfig", "http://localhost:3000,http://localhost:8080");
    }

    @Test
    void shouldNotAllowWildcardOriginWithCredentialsInCorsConfiguration() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        // Disallow wildcard pattern when allowCredentials is true
        assertThat(config.getAllowedOrigins()).doesNotContain("*");
        assertThat(config.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(config.getAllowCredentials()).isFalse();
        assertThat(config.getAllowedOrigins()).containsExactlyInAnyOrder("http://localhost:3000", "http://localhost:8080");
    }

    @Test
    void shouldRejectArbitraryAttackerOriginInCorsCheck() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/carts/me");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();

        String allowedOriginForAttacker = config.checkOrigin("https://evil-attacker.com");
        assertThat(allowedOriginForAttacker).isNull();

        String allowedOriginForTrusted = config.checkOrigin("http://localhost:3000");
        assertThat(allowedOriginForTrusted).isEqualTo("http://localhost:3000");
    }

    @Test
    void shouldExposeCorrelationIdHeaderInCors() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/orders/checkout");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getExposedHeaders()).contains("X-Correlation-Id");
    }
}
