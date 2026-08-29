package com.zendo.security.infrastructure.jwt;

import com.zendo.security.application.TokenService;
import com.zendo.security.domain.Role;
import java.util.Collection;

public interface JwtService extends TokenService {
    String generateToken(String userId, Collection<String> roles);
    String extractUserId(String token);
    Collection<String> extractRoles(String token);
    boolean isTokenValid(String token);
}
