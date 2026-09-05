package com.zendo.security.application;

import com.zendo.identity.api.IdentityQueryApi;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;

@Service
public class AuthUseCases {

    // Pre-computed BCrypt hash of an arbitrary string for constant-time comparison on nonexistent accounts
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5s9jQfKvh71Mlh.tS5w71f5Kvh71M";

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
        if (email == null || rawPassword == null) {
            throw new AuthException("Invalid credentials");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // 1. Fetch user from Identity
        var userOpt = identityQueryApi.getUserByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Mitigate timing attack by executing dummy password comparison
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new AuthException("Invalid credentials");
        }

        IdentityQueryApi.UserSummary user = userOpt.get();

        // 2. Check user status
        if ("SUSPENDED".equals(user.status()) || "DEACTIVATED".equals(user.status())) {
            throw new AuthException("Account is suspended or deactivated");
        }

        // 3. Fetch credentials
        UserCredentials credentials = credentialsRepository.findByUserId(user.id())
                .orElse(null);

        if (credentials == null) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new AuthException("Invalid credentials");
        }

        // 4. Verify password
        if (!passwordEncoder.matches(rawPassword, credentials.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        // 5. Generate token with securityVersion
        String token = tokenService.generateToken(
                credentials.getUserId(), 
                Collections.singletonList(credentials.getRole().name()),
                credentials.getSecurityVersion()
        );

        return new AuthResult(token, credentials.getUserId(), credentials.getRole().name());
    }

    @Transactional
    public void logout(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        credentialsRepository.findByUserId(userId).ifPresent(creds -> {
            credentialsRepository.save(creds.withIncrementedSecurityVersion());
        });
    }

    @Transactional
    public void revokeTokens(String userId) {
        logout(userId);
    }

    public record AuthResult(String token, String userId, String role) {}

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
