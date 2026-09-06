package com.zendo.shared.api.rest;

import com.zendo.catalog.domain.CatalogException;
import com.zendo.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        webRequest = new ServletWebRequest(new MockHttpServletRequest());
    }

    private static class SampleDomainException extends DomainException {
        public SampleDomainException(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("DomainException should be handled and return HTTP 400 Bad Request")
    void shouldHandleDomainException() {
        DomainException ex = new SampleDomainException("Stock not available");
        ResponseEntity<Object> response = handler.handleDomainException(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Stock not available", body.get("message"));
        assertNotNull(body.get("traceId"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("CatalogException as a DomainException subclass should return HTTP 400")
    void shouldHandleCatalogExceptionSubclass() {
        CatalogException ex = new CatalogException("Product not found");
        ResponseEntity<Object> response = handler.handleDomainException(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Product not found", body.get("message"));
    }

    @Test
    @DisplayName("IllegalArgumentException should return HTTP 400 Bad Request")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter value");
        ResponseEntity<Object> response = handler.handleIllegalArgumentException(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Invalid parameter value", body.get("message"));
    }

    @Test
    @DisplayName("Unhandled generic Exception should return HTTP 500 Internal Server Error")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected internal NPE");
        ResponseEntity<Object> response = handler.handleAllExceptions(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertNotNull(body.get("traceId"));
    }
}
