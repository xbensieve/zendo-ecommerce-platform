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
public class JwtStaleRoleAttackTest {

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
    @DisplayName("Downgrading user from ADMIN to CUSTOMER must immediately prevent admin access with old token")
    void roleDowngrade_immediatelyDeprivesPrivilegesWithOldToken() {
        String email = "admin_downgrade_" + UUID.randomUUID() + "@example.com";
        User user = User.createNew(email, "Admin", "User");
        userRepository.save(user);

        UserCredentials credentials = new UserCredentials(
                user.getId().value(),
                passwordEncoder.encode("Password123!"),
                Role.ADMIN,
                1
        );
        userCredentialsRepository.save(credentials);

        // Generate token claiming ADMIN
        String adminToken = tokenService.generateToken(user.getId().value(), List.of("ADMIN"), credentials.getSecurityVersion());

        // Downgrade database role from ADMIN to CUSTOMER
        UserCredentials downgraded = credentials.withRole(Role.CUSTOMER);
        userCredentialsRepository.save(downgraded);

        // Attacker attempts to use old JWT to access admin endpoint (/actuator/health or admin vendor endpoint)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Attempt admin action: PATCH vendor status
        HttpEntity<String> request = new HttpEntity<>("{\"status\":\"ACTIVE\"}", headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/vendors/" + UUID.randomUUID() + "/status",
                HttpMethod.PATCH,
                request,
                String.class
        );

        // Token must either be rejected as 401 (due to bumped securityVersion) or 403 (due to live role check)
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
