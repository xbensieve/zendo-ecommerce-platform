package com.zendo.shared.api.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("traceId", MDC.get("traceId")); // Provide traceId back to client for support

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // We could add more specific domain exception handlers here (e.g. OrderException, InventoryException)
    // mapping to 400 or 409 depending on the case. For this MVP, we capture the unhandled ones mostly.
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
        org.springframework.jdbc.CannotGetJdbcConnectionException.class,
        org.springframework.dao.CannotAcquireLockException.class,
        org.springframework.dao.DeadlockLoserDataAccessException.class,
        org.springframework.dao.PessimisticLockingFailureException.class,
        org.springframework.dao.QueryTimeoutException.class,
        org.springframework.data.redis.RedisConnectionFailureException.class
    })
    public ResponseEntity<Object> handleTransientInfrastructureExceptions(Exception ex, WebRequest request) {
        log.error("Transient infrastructure failure: {}", ex.getMessage(), ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("message", "The service is temporarily unavailable due to a transient infrastructure issue.");
        body.put("traceId", MDC.get("traceId"));

        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
