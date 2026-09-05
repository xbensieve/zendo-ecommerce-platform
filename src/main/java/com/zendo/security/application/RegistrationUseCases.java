package com.zendo.security.application;

import com.zendo.identity.api.IdentityCommandApi;
import com.zendo.security.domain.PasswordPolicy;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationUseCases {

    private final IdentityCommandApi identityCommandApi;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationUseCases(
            IdentityCommandApi identityCommandApi,
            UserCredentialsRepository userCredentialsRepository,
            PasswordEncoder passwordEncoder) {
        this.identityCommandApi = identityCommandApi;
        this.userCredentialsRepository = userCredentialsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResult register(String email, String rawPassword, String firstName, String lastName) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        PasswordPolicy.validate(rawPassword);
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("First name and last name are required");
        }

        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        try {
            // 1. Provision User in Identity
            String userId = identityCommandApi.createUser(normalizedEmail, firstName.trim(), lastName.trim());

            // 2. Hash Password
            String passwordHash = passwordEncoder.encode(rawPassword);

            // 3. Provision UserCredentials in Security (Default: CUSTOMER)
            UserCredentials credentials = new UserCredentials(userId, passwordHash, Role.CUSTOMER);
            userCredentialsRepository.save(credentials);

            return new RegistrationResult(userId, email, credentials.getRole().name());

        } catch (DataIntegrityViolationException e) {
            // Database unique constraint violation on email
            throw new DuplicateRegistrationException("Account with this email already exists");
        }
    }

    public record RegistrationResult(String userId, String email, String role) {}
}
