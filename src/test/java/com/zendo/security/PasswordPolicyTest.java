package com.zendo.security;

import com.zendo.security.domain.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    @DisplayName("Should reject null or blank passwords")
    void shouldRejectNullOrBlank() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");

        assertThatThrownBy(() -> PasswordPolicy.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678", "123456789", "Abcdefgh9"})
    @DisplayName("Should reject passwords shorter than 10 characters")
    void shouldRejectShortPasswords(String shortPass) {
        assertThatThrownBy(() -> PasswordPolicy.validate(shortPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 10 and 128 characters");
    }

    @Test
    @DisplayName("Should reject passwords longer than 128 characters")
    void shouldRejectLongPasswords() {
        String longPass = "a".repeat(129);
        assertThatThrownBy(() -> PasswordPolicy.validate(longPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 10 and 128 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password1234",
            "PASSWORD1234",
            "1234567890",
            "123456789012",
            "qwertyuiop",
            "admin12345",
            "welcome1234",
            "letmein123",
            "monkey1234",
            "sunshine123",
            "princess123",
            "football123",
            "iloveyou123"
    })
    @DisplayName("Should reject common guessable passwords case-insensitively")
    void shouldRejectCommonPasswords(String commonPass) {
        assertThatThrownBy(() -> PasswordPolicy.validate(commonPass))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("easily guessable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MySecretP@ssword2026!",
            "C0rrect-H0rse-Battery-Staple!",
            "SuperStrongEnterprisePass#99"
    })
    @DisplayName("Should accept strong passwords meeting length and complexity guidelines")
    void shouldAcceptStrongPasswords(String validPass) {
        assertThatCode(() -> PasswordPolicy.validate(validPass)).doesNotThrowAnyException();
    }
}
