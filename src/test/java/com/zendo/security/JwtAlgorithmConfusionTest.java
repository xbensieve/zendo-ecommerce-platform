package com.zendo.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.zendo.security.infrastructure.jwt.JwtService;
import com.zendo.security.infrastructure.jwt.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtAlgorithmConfusionTest {

    private static final String SECRET = "valid-hmac256-signing-secret-key-32-chars!!";
    private static final String ISSUER = "zendo-test";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(SECRET, ISSUER, 3600000);
    }

    @Test
    @DisplayName("Should reject token with alg: none")
    void shouldRejectAlgNoneToken() {
        String noneToken = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("victim-user")
                .withClaim("roles", List.of("ADMIN"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.none());

        assertFalse(jwtService.isTokenValid(noneToken), "Tokens with alg: none must be rejected");
    }

    @Test
    @DisplayName("Should reject token signed with different secret or wrong algorithm")
    void shouldRejectWrongSecretToken() {
        Algorithm wrongAlgorithm = Algorithm.HMAC256("attacker-secret-key-32-characters!!");
        String forgedToken = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("victim-user")
                .withClaim("roles", List.of("ADMIN"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(wrongAlgorithm);

        assertFalse(jwtService.isTokenValid(forgedToken), "Forged tokens with wrong secret must be rejected");
    }

    @Test
    @DisplayName("Should reject token with wrong issuer")
    void shouldRejectWrongIssuerToken() {
        String forgedIssuerToken = JWT.create()
                .withIssuer("evil-issuer")
                .withSubject("user-1")
                .withClaim("roles", List.of("CUSTOMER"))
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256(SECRET));

        assertFalse(jwtService.isTokenValid(forgedIssuerToken), "Tokens with invalid issuer must be rejected");
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        String expiredToken = JWT.create()
                .withIssuer(ISSUER)
                .withSubject("user-1")
                .withClaim("roles", List.of("CUSTOMER"))
                .withExpiresAt(new Date(System.currentTimeMillis() - 10000)) // expired 10s ago
                .sign(Algorithm.HMAC256(SECRET));

        assertFalse(jwtService.isTokenValid(expiredToken), "Expired tokens must be rejected");
    }

    @Test
    @DisplayName("Should reject token with tampered payload")
    void shouldRejectTamperedPayloadToken() {
        String validToken = jwtService.generateToken("customer-1", List.of("CUSTOMER"), 1);
        String[] parts = validToken.split("\\.");
        // Attacker alters payload part (base64)
        String tamperedToken = parts[0] + ".eyJzdWIiOiJhZG1pbi11c2VyIiwicm9sZXMiOlsiQURNSU4iXX0." + parts[2];

        assertFalse(jwtService.isTokenValid(tamperedToken), "Tampered payload token must fail signature verification");
    }
}
