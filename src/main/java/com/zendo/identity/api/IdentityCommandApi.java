package com.zendo.identity.api;

/**
 * Explicit application contract for creating Identity resources across bounded contexts.
 * Specifically used by Security during Registration.
 */
public interface IdentityCommandApi {
    
    /**
     * Provisions a new Identity User.
     * 
     * @param email login identifier
     * @param firstName user's first name
     * @param lastName user's last name
     * @return the string representation of the created UserId
     * @throws org.springframework.dao.DataIntegrityViolationException if email already exists
     */
    String createUser(String email, String firstName, String lastName);
}
