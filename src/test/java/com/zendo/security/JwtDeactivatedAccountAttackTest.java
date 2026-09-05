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
public class JwtDeactivatedAccountAttackTest {

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
    @DisplayName("Deactivating an active account must immediately reject any existing JWT with 401 Unauthorized")
    void deactivatedAccount_immediatelyRejectsExistingJwt() {
        String email = "deactivate_attack_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Victim", "Deactivated");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.CUSTOMER
        );
        userCredentialsRepository.save(credentials);

        String jwtToken = tokenService.generateToken(user.getId().value(), List.of("CUSTOMER"), credentials.getSecurityVersion());

        // Verify token works before deactivation
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        ResponseEntity<String> beforeDeactivate = restTemplate.exchange(
                "/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        assertThat(beforeDeactivate.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Deactivate user in identity repository
        user.deactivate();
        userRepository.save(user);

        // Attacker attempts to reuse the unexpired JWT
        ResponseEntity<String> afterDeactivate = restTemplate.exchange(
                "/api/v1/carts/me", HttpMethod.GET, new HttpEntity<>(headers), String.class
        );

        assertThat(afterDeactivate.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
