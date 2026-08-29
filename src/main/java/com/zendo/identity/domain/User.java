package com.zendo.identity.domain;

import java.time.Instant;

/**
 * User aggregate root.
 * Very basic implementation for MVP so other contexts can reference it.
 */
public class User {
    private final UserId id;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private final Instant createdAt;

    public User(UserId id, String email, String firstName, String lastName, UserStatus status, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("UserStatus cannot be null");
        }
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static User createNew(String email, String firstName, String lastName) {
        return new User(
                new UserId(java.util.UUID.randomUUID().toString()), 
                email, 
                firstName, 
                lastName, 
                UserStatus.ACTIVE, 
                Instant.now()
        );
    }

    public void suspend() {
        if (this.status == UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Cannot suspend a deactivated user");
        }
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        if (this.status == UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Cannot reactivate a deactivated user");
        }
        if (this.status == UserStatus.ACTIVE) {
            return; // Already active, idempotent
        }
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        if (this.status == UserStatus.DEACTIVATED) {
            return; // Already deactivated, idempotent
        }
        this.status = UserStatus.DEACTIVATED;
    }

    public UserId getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
