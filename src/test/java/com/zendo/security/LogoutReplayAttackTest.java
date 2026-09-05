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
public class LogoutReplayAttackTest {

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
    @DisplayName("Calling logout must immediately invalidate token; subsequent replay must return 401")
    void logout_mustInvalidateToken_andRejectReplay() {
        String email = "logout_replay_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Logout", "Attacker");
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

        // 1. Verify token works initially
        ResponseEntity<String> initialReq = restTemplate.exchange(
                "/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        assertThat(initialReq.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Perform logout
        ResponseEntity<String> logoutResp = restTemplate.exchange(
                "/api/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class
        );
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Attacker replays old JWT token
        ResponseEntity<String> replayReq = restTemplate.exchange(
                "/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        assertThat(replayReq.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
