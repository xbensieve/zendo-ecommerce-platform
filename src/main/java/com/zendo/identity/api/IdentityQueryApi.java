package com.zendo.identity.api;

import java.util.Optional;

/**
 * Public query API for the identity module.
 */
public interface IdentityQueryApi {

    record UserSummary(String id, String email, String firstName, String lastName, String status) {}

    Optional<UserSummary> getUserById(String userId);
    
    Optional<UserSummary> getUserByEmail(String email);
}
