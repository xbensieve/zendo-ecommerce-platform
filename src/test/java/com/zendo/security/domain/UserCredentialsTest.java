package com.zendo.security.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserCredentialsTest {

    @Test
    void shouldCreateUserCredentialsSuccessfully() {
        UserCredentials credentials = new UserCredentials("usr_123", "hashed_pwd", Role.CUSTOMER);

        assertEquals("usr_123", credentials.getUserId());
        assertEquals("hashed_pwd", credentials.getPasswordHash());
        assertEquals(Role.CUSTOMER, credentials.getRole());
    }

    @Test
    void shouldRejectInvalidUserId() {
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials(null, "hash", Role.CUSTOMER));
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("", "hash", Role.CUSTOMER));
    }

    @Test
    void shouldRejectInvalidPasswordHash() {
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("usr_123", null, Role.CUSTOMER));
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("usr_123", "", Role.CUSTOMER));
    }

    @Test
    void shouldRejectNullRole() {
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("usr_123", "hash", null));
    }
}
