package com.zendo.shared.exception;

/**
 * Base abstract class for all domain and business rule violations across bounded contexts.
 * Domain exceptions represent recoverable or non-fatal business rule failures that 
 * typically map to HTTP 400 Bad Request in client-facing APIs.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
