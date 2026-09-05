package com.zendo.security;

import com.zendo.shared.messaging.EventSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventSigningSecretRequiredTest {

    @Test
    @DisplayName("Should fail when signing secret is null or empty")
    void shouldFailWhenSecretIsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new EventSigner(null));
        assertThrows(IllegalArgumentException.class, () -> new EventSigner(""));
        assertThrows(IllegalArgumentException.class, () -> new EventSigner("   "));
    }

    @Test
    @DisplayName("Should fail when signing secret has insufficient length (< 32 chars)")
    void shouldFailWhenSecretIsTooShort() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSigner("short-secret-key-under-32-char")
        );
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    @DisplayName("Should fail in production profile when using known development secret")
    void shouldFailInProductionWithDevSecret() {
        Environment prodEnv = mock(Environment.class);
        when(prodEnv.getActiveProfiles()).thenReturn(new String[]{"prod"});

        IllegalStateException ex1 = assertThrows(
                IllegalStateException.class,
                () -> new EventSigner("internal-event-signing-key-secret-32-chars-min", prodEnv)
        );
        assertTrue(ex1.getMessage().contains("Production environment detected"));

        IllegalStateException ex2 = assertThrows(
                IllegalStateException.class,
                () -> new EventSigner("dev-secret-key-do-not-use-in-production-random-suffix", prodEnv)
        );
        assertTrue(ex2.getMessage().contains("Production environment detected"));
    }

    @Test
    @DisplayName("Should succeed in production profile when high-entropy secret is configured")
    void shouldSucceedInProductionWithStrongSecret() {
        Environment prodEnv = mock(Environment.class);
        when(prodEnv.getActiveProfiles()).thenReturn(new String[]{"prod"});

        EventSigner signer = new EventSigner("production-strong-entropy-secret-key-64-bytes-super-safe!!", prodEnv);
        assertNotNull(signer);
        String payload = "{\"test\":true}";
        String sig = signer.sign(payload);
        assertTrue(signer.verify(payload, sig));
    }
}
