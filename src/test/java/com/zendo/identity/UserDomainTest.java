package com.zendo.identity;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserId;
import com.zendo.identity.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserDomainTest {

    @Test
    void shouldCreateValidUser() {
        UserId id = new UserId(UUID.randomUUID().toString());
        User user = new User(id, "test@example.com", "John", "Doe", UserStatus.ACTIVE, Instant.now());
        
        assertEquals(id, user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void shouldFailIfUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> 
                new User(null, "test@example.com", "John", "Doe", UserStatus.ACTIVE, Instant.now()));
    }

    @Test
    void shouldFailIfEmailIsBlank() {
        UserId id = new UserId(UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> 
                new User(id, "   ", "John", "Doe", UserStatus.ACTIVE, Instant.now()));
    }

    @Test
    void shouldFailIfStatusIsNull() {
        UserId id = new UserId(UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> 
                new User(id, "test@example.com", "John", "Doe", null, Instant.now()));
    }

    @Test
    void shouldCreateNewUser() {
        User user = User.createNew("test2@example.com", "Jane", "Doe");
        assertNotNull(user.getId());
        assertNotNull(user.getId().value());
        assertEquals("test2@example.com", user.getEmail());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void shouldSuspendActiveUser() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.suspend();
        
        assertEquals(UserStatus.SUSPENDED, user.getStatus());
    }

    @Test
    void shouldReactivateSuspendedUser() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.suspend();
        user.reactivate();
        
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void shouldDeactivateUser() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.deactivate();
        
        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
    }

    @Test
    void shouldNotSuspendDeactivatedUser() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.deactivate();
        
        assertThrows(IllegalStateException.class, user::suspend);
    }

    @Test
    void shouldNotReactivateDeactivatedUser() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.deactivate();
        
        assertThrows(IllegalStateException.class, user::reactivate);
    }

    @Test
    void shouldBeIdempotentForReactivateAndDeactivate() {
        User user = User.createNew("test@example.com", "John", "Doe");
        user.reactivate();
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        
        user.deactivate();
        user.deactivate();
        assertEquals(UserStatus.DEACTIVATED, user.getStatus());
    }
}
