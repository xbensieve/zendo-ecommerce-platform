package com.zendo.security.domain;

public class UserCredentials {
    private final String userId;
    private final String passwordHash;
    private final Role role;
    private final int securityVersion;

    public UserCredentials(String userId, String passwordHash, Role role) {
        this(userId, passwordHash, role, 1);
    }

    public UserCredentials(String userId, String passwordHash, Role role, int securityVersion) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (securityVersion < 1) {
            throw new IllegalArgumentException("Security version must be at least 1");
        }
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.securityVersion = securityVersion;
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

    public int getSecurityVersion() {
        return securityVersion;
    }

    public UserCredentials withIncrementedSecurityVersion() {
        return new UserCredentials(this.userId, this.passwordHash, this.role, this.securityVersion + 1);
    }

    public UserCredentials withRole(Role newRole) {
        return new UserCredentials(this.userId, this.passwordHash, newRole, this.securityVersion + 1);
    }

    public UserCredentials withPassword(String newPasswordHash) {
        return new UserCredentials(this.userId, newPasswordHash, this.role, this.securityVersion + 1);
    }
}
