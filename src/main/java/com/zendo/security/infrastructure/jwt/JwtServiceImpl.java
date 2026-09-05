package com.zendo.security.infrastructure.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.zendo.security.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtServiceImpl implements JwtService {

    private final String secret;
    private final String issuer;
    private final long expirationMillis;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtServiceImpl(
            @Value("${zendo.security.jwt.secret}") String secret,
            @Value("${zendo.security.jwt.issuer:zendo}") String issuer,
            @Value("${zendo.security.jwt.expiration-ms:3600000}") long expirationMillis) {
        this.secret = secret;
        this.issuer = issuer;
        this.expirationMillis = expirationMillis;
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
    }

    @Override
    public String generateToken(String userId, Collection<String> roles) {
        return generateToken(userId, roles, 1);
    }

    @Override
    public String generateToken(String userId, Collection<String> roles, int securityVersion) {
        List<String> roleNames = roles.stream().collect(Collectors.toList());
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(userId)
                .withClaim("roles", roleNames)
                .withClaim("securityVersion", securityVersion)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMillis))
                .sign(algorithm);
    }

    @Override
    public String extractUserId(String token) {
        return decode(token).getSubject();
    }

    @Override
    public Collection<String> extractRoles(String token) {
        return decode(token).getClaim("roles").asList(String.class);
    }

    @Override
    public Integer extractSecurityVersion(String token) {
        var claim = decode(token).getClaim("securityVersion");
        return claim.isNull() ? null : claim.asInt();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            decode(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    private DecodedJWT decode(String token) {
        return verifier.verify(token);
    }
}
