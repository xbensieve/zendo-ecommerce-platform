package com.zendo.security.infrastructure.spring;

import com.zendo.identity.api.IdentityQueryApi;
import com.zendo.security.domain.UserCredentials;
import com.zendo.security.domain.UserCredentialsRepository;
import com.zendo.security.infrastructure.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final IdentityQueryApi identityQueryApi;
    private final UserCredentialsRepository credentialsRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            IdentityQueryApi identityQueryApi,
            UserCredentialsRepository credentialsRepository) {
        this.jwtService = jwtService;
        this.identityQueryApi = identityQueryApi;
        this.credentialsRepository = credentialsRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        
        if (jwtService.isTokenValid(token)) {
            String userId = jwtService.extractUserId(token);
            Integer tokenSecurityVersion = jwtService.extractSecurityVersion(token);
            
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 1. Verify account status via Identity module
                Optional<IdentityQueryApi.UserSummary> userOpt = identityQueryApi.getUserById(userId);
                if (userOpt.isEmpty() || !"ACTIVE".equals(userOpt.get().status())) {
                    log.warn("JWT rejected: user {} not found or inactive (status: {})", 
                            userId, userOpt.map(IdentityQueryApi.UserSummary::status).orElse("NONE"));
                } else {
                    // 2. Verify securityVersion and authoritative role via UserCredentials
                    Optional<UserCredentials> credsOpt = credentialsRepository.findByUserId(userId);
                    if (credsOpt.isEmpty()) {
                        log.warn("JWT rejected: credentials for user {} not found", userId);
                    } else {
                        UserCredentials creds = credsOpt.get();
                        if (tokenSecurityVersion == null || tokenSecurityVersion != creds.getSecurityVersion()) {
                            log.warn("JWT rejected: token securityVersion ({}) does not match current version ({}) for user {}", 
                                    tokenSecurityVersion, creds.getSecurityVersion(), userId);
                        } else {
                            // 3. Bind live role from credentials to prevent stale privilege escalation
                            ZendoUserDetails userDetails = new ZendoUserDetails(
                                    userId, 
                                    Collections.singletonList(creds.getRole().name())
                            );
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            MDC.put("userId", userId);
                        }
                    }
                }
            }
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
