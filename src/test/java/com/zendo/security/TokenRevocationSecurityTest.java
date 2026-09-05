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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class TokenRevocationSecurityTest {

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
    @DisplayName("Logout immediately invalidates the current JWT by bumping securityVersion")
    void logout_immediatelyInvalidatesJwt() {
        String email = "logout_test_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Logout", "User");
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

        // 1. Initial request with JWT succeeds
        ResponseEntity<String> initialRes = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(initialRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Perform Logout
        ResponseEntity<String> logoutRes = restTemplate.exchange("/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Reuse old JWT -> MUST BE REJECTED with 401 Unauthorized
        ResponseEntity<String> postLogoutRes = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(postLogoutRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Token with stale securityVersion is rejected immediately")
    void tokenWithStaleSecurityVersion_isRejected() {
        String email = "stale_version_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Stale", "Version");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        // Issue token with securityVersion = 1
        String oldToken = tokenService.generateToken(user.getId().value(), List.of("CUSTOMER"), 1);

        // Bump securityVersion in database to 2
        UserCredentials bumped = credentials.withIncrementedSecurityVersion();
        userCredentialsRepository.save(bumped);

        // Old token presented to API must be rejected
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oldToken);
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
