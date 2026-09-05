package com.zendo.security.domain;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "1234567890",
            "12345678901",
            "123456789012",
            "password123",
            "password1234",
            "passwords123",
            "qwertyuiop",
            "admin12345",
            "welcome1234",
            "letmein123",
            "monkey1234",
            "sunshine123",
            "princess123",
            "football123",
            "iloveyou123",
            "trustno1123",
            "starwars123",
            "master1234",
            "dragon1234",
            "shadow1234",
            "superman123",
            "michael1234",
            "jordan1234",
            "hunter21234",
            "pass123456",
            "secret1234",
            "default1234"
    );

    private PasswordPolicy() {}

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Password must be between %d and %d characters", MIN_LENGTH, MAX_LENGTH));
        }
        String normalized = rawPassword.trim().toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(normalized)) {
            throw new IllegalArgumentException("Password is too common and easily guessable");
        }
    }
}
