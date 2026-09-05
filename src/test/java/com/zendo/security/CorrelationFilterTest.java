package com.zendo.security;

import com.zendo.security.infrastructure.spring.CorrelationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CorrelationFilterTest {

    private CorrelationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationFilter();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void shouldAcceptSafeCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-abc-123_XYZ");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldGenerateNewCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        assertThat(correlationId).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    void shouldSanitizeAndRejectCrlfLogInjectionAttempt() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "attack\r\nInjected-Header: evil\r\n\r\n<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-Id");
        // Must be replaced with a clean generated UUID and not echo the CRLF injection
        assertThat(correlationId).doesNotContain("\r");
        assertThat(correlationId).doesNotContain("\n");
        assertThat(correlationId).doesNotContain("evil");
        assertThat(correlationId).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    void shouldRejectOversizedCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String oversized = "a".repeat(100);
        request.addHeader("X-Correlation-Id", oversized);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotEqualTo(oversized);
        assertThat(correlationId).matches("^[0-9a-fA-F-]{36}$");
    }
}
