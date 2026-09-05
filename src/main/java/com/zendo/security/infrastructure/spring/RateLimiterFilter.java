package com.zendo.security.infrastructure.spring;

import com.zendo.shared.security.DistributedRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final DistributedRateLimiter rateLimiter;

    @Value("${zendo.security.rate-limiter.enabled:true}")
    private boolean enabled = true;

    public RateLimiterFilter(DistributedRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        initTrustedProxies("127.0.0.1,::1,10.0.0.0/8");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawUri = request.getRequestURI();
        String uri = rawUri != null ? rawUri.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("/+$", "") : "";
        String method = request.getMethod();

        // 1. Login rate limit: 5 requests / minute per IP
        if ("POST".equalsIgnoreCase(method) && uri.endsWith("/api/v1/auth/login")) {
            String ip = extractClientIp(request);
            String key = "rl:auth:login:" + ip;
            if (!rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT)) {
                rejectTooManyRequests(response, "Too many login attempts. Please try again later.");
                return;
            }
        }
        // 2. Register rate limit: 5 requests / minute per IP
        else if ("POST".equalsIgnoreCase(method) && uri.endsWith("/api/v1/auth/register")) {
            String ip = extractClientIp(request);
            String key = "rl:auth:register:" + ip;
            if (!rateLimiter.isAllowed(key, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT)) {
                rejectTooManyRequests(response, "Too many registration attempts. Please try again later.");
                return;
            }
        }
        // 3. Flash sale checkout: 3 requests / minute per authenticated user (FAIL_CLOSED if Redis unavailable)
        else if ("POST".equalsIgnoreCase(method) && (uri.contains("/api/v1/orders/flash-sale") || uri.contains("/api/v1/orders/flash-sales"))) {
            String userKey = resolveUserOrIpKey(request, "rl:order:flashsale:");
            if (!rateLimiter.isAllowed(userKey, 3, 60, DistributedRateLimiter.RateLimitPolicy.FAIL_CLOSED)) {
                rejectTooManyRequests(response, "Too many flash sale checkout requests. Please wait before retrying.");
                return;
            }
        }
        // 4. Standard checkout: 5 requests / minute per authenticated user
        else if ("POST".equalsIgnoreCase(method) && uri.endsWith("/api/v1/orders/checkout")) {
            String userKey = resolveUserOrIpKey(request, "rl:order:checkout:");
            if (!rateLimiter.isAllowed(userKey, 5, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL_STRICT)) {
                rejectTooManyRequests(response, "Too many checkout requests. Please wait before retrying.");
                return;
            }
        }
        // 5. Global API baseline protection per IP: 300 requests / minute
        else if (uri.startsWith("/api/")) {
            String ip = extractClientIp(request);
            String key = "rl:api:" + ip;
            if (!rateLimiter.isAllowed(key, 300, 60, DistributedRateLimiter.RateLimitPolicy.DEGRADE_LOCAL)) {
                rejectTooManyRequests(response, "Too many requests. Please slow down.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Value("${zendo.security.rate-limiter.trusted-proxies:127.0.0.1,::1,10.0.0.0/8}")
    private String trustedProxiesConfig;

    private final java.util.List<IpMatcher> trustedMatchers = new java.util.concurrent.CopyOnWriteArrayList<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        if (trustedProxiesConfig != null && !trustedProxiesConfig.isBlank()) {
            initTrustedProxies(trustedProxiesConfig);
        }
    }

    public void setTrustedProxies(String config) {
        this.trustedProxiesConfig = config;
        initTrustedProxies(config);
    }

    private void initTrustedProxies(String config) {
        trustedMatchers.clear();
        if (config == null || config.isBlank()) return;
        for (String entry : config.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            try {
                if (trimmed.contains("/")) {
                    trustedMatchers.add(new CidrMatcher(trimmed));
                } else {
                    trustedMatchers.add(new ExactIpMatcher(trimmed));
                }
            } catch (Exception e) {
                // Ignore malformed configuration entries
            }
        }
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String cleanIp = ip.trim();
        for (IpMatcher matcher : trustedMatchers) {
            if (matcher.matches(cleanIp)) return true;
        }
        return false;
    }

    private String resolveUserOrIpKey(HttpServletRequest request, String prefix) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return prefix + auth.getName();
        }
        return prefix + extractClientIp(request);
    }

    public String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }
        remoteAddr = remoteAddr.trim();

        // 1. Direct connection from untrusted client: IGNORE all forwarded headers to prevent spoofing
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. Request from trusted reverse proxy: resolve genuine client IP from forwarded headers
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank() && isValidIp(xRealIp.trim())) {
                return xRealIp.trim();
            }
            return remoteAddr;
        }

        // Walk X-Forwarded-For from right to left, skipping intermediate trusted proxies
        String[] parts = xff.split(",");
        String clientIp = null;
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (candidate.isEmpty()) continue;
            if (!isValidIp(candidate)) continue;
            if (!isTrustedProxy(candidate)) {
                clientIp = candidate;
                break;
            }
        }

        if (clientIp != null) {
            return clientIp;
        }

        // If all entries in XFF were trusted proxies, fallback to leftmost valid IP
        for (String part : parts) {
            String candidate = part.trim();
            if (isValidIp(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) return false;
        if (!ip.matches("^[0-9a-fA-F:.]+$")) return false;
        try {
            java.net.InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private interface IpMatcher {
        boolean matches(String ip);
    }

    private static class ExactIpMatcher implements IpMatcher {
        private final String exactIp;

        ExactIpMatcher(String exactIp) {
            this.exactIp = exactIp;
        }

        @Override
        public boolean matches(String ip) {
            return exactIp.equalsIgnoreCase(ip);
        }
    }

    private static class CidrMatcher implements IpMatcher {
        private final byte[] networkBytes;
        private final int prefixLength;

        CidrMatcher(String cidr) throws java.net.UnknownHostException {
            String[] parts = cidr.split("/");
            java.net.InetAddress addr = java.net.InetAddress.getByName(parts[0].trim());
            this.networkBytes = addr.getAddress();
            this.prefixLength = Integer.parseInt(parts[1].trim());
        }

        @Override
        public boolean matches(String ip) {
            try {
                java.net.InetAddress addr = java.net.InetAddress.getByName(ip.trim());
                byte[] targetBytes = addr.getAddress();
                if (targetBytes.length != networkBytes.length) return false;
                int fullBytes = prefixLength / 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (targetBytes[i] != networkBytes[i]) return false;
                }
                int remainingBits = prefixLength % 8;
                if (remainingBits > 0) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    if ((targetBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void rejectTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", message));
    }
}
