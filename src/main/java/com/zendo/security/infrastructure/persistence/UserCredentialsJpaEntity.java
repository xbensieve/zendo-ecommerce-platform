package com.zendo.security.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_credentials", schema = "security_ctx")
public class UserCredentialsJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "security_version", nullable = false)
    private int securityVersion;

    protected UserCredentialsJpaEntity() {}

    public UserCredentialsJpaEntity(String userId, String passwordHash, String role) {
        this(userId, passwordHash, role, 1);
    }

    public UserCredentialsJpaEntity(String userId, String passwordHash, String role, int securityVersion) {
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

    public String getRole() {
        return role;
    }

    public int getSecurityVersion() {
        return securityVersion;
    }
}
