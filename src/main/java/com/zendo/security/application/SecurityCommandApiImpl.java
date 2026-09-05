package com.zendo.security.application;

import com.zendo.security.api.SecurityCommandApi;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityCommandApiImpl implements SecurityCommandApi {

    private final UserCredentialsRepository credentialsRepository;

    public SecurityCommandApiImpl(UserCredentialsRepository credentialsRepository) {
        this.credentialsRepository = credentialsRepository;
    }

    @Override
    @Transactional
    public void upgradeToVendorIfCustomer(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        credentialsRepository.findByUserId(userId).ifPresent(creds -> {
            if (creds.getRole() == Role.CUSTOMER) {
                credentialsRepository.save(creds.withRole(Role.VENDOR));
            }
        });
    }

    @Override
    @Transactional
    public void updateUserRole(String userId, String newRole) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be blank");
        }
        if (newRole == null || newRole.isBlank()) {
            throw new IllegalArgumentException("Role cannot be blank");
        }
        Role targetRole;
        try {
            targetRole = Role.valueOf(newRole.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + newRole);
        }

        UserCredentials creds = credentialsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User credentials not found for user ID: " + userId));

        credentialsRepository.save(creds.withRole(targetRole));
    }
}
