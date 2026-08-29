package com.zendo.security.domain;

public class UserCredentials {
    private final String userId;
    private final String passwordHash;
    private final Role role;

    public UserCredentials(String userId, String passwordHash, Role role) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }
}
