package com.zendo.security;

import com.zendo.identity.domain.User;
import com.zendo.identity.domain.UserRepository;
import com.zendo.security.application.TokenService;
import com.zendo.security.domain.Role;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AccountStatusSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository userCredentialsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("Active user can access protected endpoint with valid JWT")
    void activeUser_withValidJwt_canAccessProtectedEndpoint() {
        String email = "active_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Active", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        String token = tokenService.generateToken(user.getId().value(), List.of("CUSTOMER"), credentials.getSecurityVersion());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Suspending an active user immediately rejects their existing non-expired JWT")
    void suspendedUser_withExistingJwt_isImmediatelyRejected() {
        String email = "to_suspend_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "ToSuspend", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        String token = tokenService.generateToken(user.getId().value(), List.of("CUSTOMER"), credentials.getSecurityVersion());

        // First verify valid access
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> initialResponse = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(initialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Attack: User is suspended in identity domain
        user.suspend();
        userRepository.save(user);

        // Second request with same JWT must be immediately rejected (401 Unauthorized)
        ResponseEntity<String> postSuspensionResponse = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(postSuspensionResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Deactivating an active user immediately rejects their existing non-expired JWT")
    void deactivatedUser_withExistingJwt_isImmediatelyRejected() {
        String email = "to_deactivate_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "ToDeactivate", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        String token = tokenService.generateToken(user.getId().value(), List.of("CUSTOMER"), credentials.getSecurityVersion());

        // Deactivate user
        user.deactivate();
        userRepository.save(user);

        // Request with JWT must fail
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Role downgrade (ADMIN -> CUSTOMER) immediately revokes admin privileges even with old admin JWT")
    void roleDowngrade_immediatelyRevokesPrivileges_withStaleJwt() {
        String email = "admin_downgrade_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Admin", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.ADMIN
        );
        userCredentialsRepository.save(credentials);

        // JWT is issued with role ADMIN
        String adminToken = tokenService.generateToken(user.getId().value(), List.of("ADMIN"), credentials.getSecurityVersion());

        // Downgrade user's role in database to CUSTOMER while keeping securityVersion the same
        // to specifically prove that live role re-query from database overrides token's ADMIN claim
        UserCredentials downgraded = new UserCredentials(
                user.getId().value(),
                credentials.getPasswordHash(),
                Role.CUSTOMER,
                credentials.getSecurityVersion()
        );
        userCredentialsRepository.save(downgraded);

        // Attempt to access an ADMIN-only endpoint using the token claiming ADMIN role
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // PATCH /api/v1/vendors/{id}/status is hasRole('ADMIN')
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/vendors/test-vendor/status",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"ACTIVE\"}", headers),
                String.class
        );

        // Must be rejected with 403 Forbidden because live role in database is CUSTOMER (token's ADMIN claim is untrusted)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
