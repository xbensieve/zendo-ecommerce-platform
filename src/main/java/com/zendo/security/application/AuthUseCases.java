package com.zendo.security.application;

import com.zendo.identity.api.IdentityQueryApi;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthUseCases {

    private final IdentityQueryApi identityQueryApi;
    private final UserCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthUseCases(
            IdentityQueryApi identityQueryApi,
            UserCredentialsRepository credentialsRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.identityQueryApi = identityQueryApi;
        this.credentialsRepository = credentialsRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public AuthResult login(String email, String rawPassword) {
        // 1. Fetch user from Identity to check status and get stable UserId
        IdentityQueryApi.UserSummary user = identityQueryApi.getUserByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        // 2. Check user status
        if ("SUSPENDED".equals(user.status()) || "DEACTIVATED".equals(user.status())) {
            throw new AuthException("User is not active");
        }

        // 3. Fetch credentials
        UserCredentials credentials = credentialsRepository.findByUserId(user.id())
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        // 4. Verify password
        if (!passwordEncoder.matches(rawPassword, credentials.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        // 5. Generate token
        String token = tokenService.generateToken(credentials.getUserId(), Collections.singletonList(credentials.getRole().name()));

        return new AuthResult(token, credentials.getUserId(), credentials.getRole().name());
    }

    public record AuthResult(String token, String userId, String role) {}

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
