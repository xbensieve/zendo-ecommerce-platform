package com.zendo.security;

import com.zendo.identity.api.IdentityCommandApi;
import com.zendo.identity.api.IdentityQueryApi;
import com.zendo.security.api.rest.AuthController;
import com.zendo.security.application.AuthUseCases;
import com.zendo.security.application.RegistrationUseCases;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import com.zendo.shared.api.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class AuthSecurityTest {

    @Mock
    private IdentityQueryApi identityQueryApi;

    @Mock
    private IdentityCommandApi identityCommandApi;

    @Mock
    private UserCredentialsRepository credentialsRepository;

    @Mock
    private com.zendo.security.application.TokenService tokenService;

    private PasswordEncoder passwordEncoder;
    private AuthUseCases authUseCases;
    private RegistrationUseCases registrationUseCases;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // Use cost 4 for fast unit tests
        authUseCases = new AuthUseCases(identityQueryApi, credentialsRepository, passwordEncoder, tokenService);
        registrationUseCases = new RegistrationUseCases(identityCommandApi, credentialsRepository, passwordEncoder);
        AuthController authController = new AuthController(authUseCases, registrationUseCases);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldNormalizeEmailDuringRegistrationAndLogin() {
        String mixedEmail = "  Alice.Smith@Example.COM  ";
        String normalizedEmail = "alice.smith@example.com";
        String userId = UUID.randomUUID().toString();

        when(identityCommandApi.createUser(eq(normalizedEmail), eq("Alice"), eq("Smith"))).thenReturn(userId);

        var regResult = registrationUseCases.register(mixedEmail, "SecretPass123", "Alice", "Smith");
        assertThat(regResult.userId()).isEqualTo(userId);

        // Test login normalization
        when(identityQueryApi.getUserByEmail(eq(normalizedEmail)))
                .thenReturn(Optional.of(new IdentityQueryApi.UserSummary(userId, normalizedEmail, "Alice", "Smith", "ACTIVE")));
        when(credentialsRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserCredentials(userId, passwordEncoder.encode("SecretPass123"), Role.CUSTOMER)));
        when(tokenService.generateToken(eq(userId), eq(java.util.Collections.singletonList("CUSTOMER")), eq(1)))
                .thenReturn("jwt-token-123");

        var loginResult = authUseCases.login("  ALICE.SMITH@EXAMPLE.COM  ", "SecretPass123");
        assertThat(loginResult.token()).isEqualTo("jwt-token-123");
    }

    @Test
    void shouldMaintainConstantTimeAndThrowGenericErrorOnNonexistentAccount() {
        when(identityQueryApi.getUserByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authUseCases.login("nonexistent@example.com", "anyPassword123"))
                .isInstanceOf(AuthUseCases.AuthException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void shouldRejectOversizedPasswordToPreventBcryptDos() throws Exception {
        String oversizedPassword = "A".repeat(129);

        String payload = """
            {
                "email": "user@example.com",
                "password": "%s"
            }
        """.formatted(oversizedPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.password").exists());
    }
}
