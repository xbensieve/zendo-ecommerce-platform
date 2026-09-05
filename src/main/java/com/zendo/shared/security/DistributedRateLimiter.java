package com.zendo.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimiter.class);

    private static final String RATE_LIMIT_LUA =
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if tonumber(current) == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;
    private final ConcurrentMap<String, LocalCounter> localFallback = new ConcurrentHashMap<>();

    @Autowired
    public DistributedRateLimiter(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(RATE_LIMIT_LUA);
        this.redisScript.setResultType(Long.class);
    }

    public enum RateLimitPolicy {
        FAIL_CLOSED,
        DEGRADE_LOCAL_STRICT,
        DEGRADE_LOCAL
    }

    /**
     * Checks whether a request under the given key should be allowed with default DEGRADE_LOCAL policy.
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        return isAllowed(key, maxRequests, windowSeconds, RateLimitPolicy.DEGRADE_LOCAL);
    }

    /**
     * Checks whether a request under the given key should be allowed with an explicit resilience policy.
     *
     * @param key           Distributed rate-limiting key
     * @param maxRequests   Maximum requests allowed within the window
     * @param windowSeconds Window duration in seconds
     * @param policy        Policy to apply when Redis is unavailable
     * @return true if allowed, false if rejected
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds, RateLimitPolicy policy) {
        if (redisTemplate != null) {
            try {
                Long current = redisTemplate.execute(
                        redisScript,
                        Collections.singletonList(key),
                        String.valueOf(windowSeconds)
                );
                return current != null && current <= maxRequests;
            } catch (Exception e) {
                log.warn("Redis rate-limiting failed for key '{}': {}. Applying resilience policy: {}", key, e.getMessage(), policy);
                if (policy == RateLimitPolicy.FAIL_CLOSED) {
                    return false;
                }
                if (policy == RateLimitPolicy.DEGRADE_LOCAL_STRICT) {
                    int strictLocalLimit = Math.max(1, Math.min(maxRequests, 2));
                    return isAllowedLocally(key, strictLocalLimit, windowSeconds);
                }
            }
        } else {
            if (policy == RateLimitPolicy.FAIL_CLOSED) {
                return false;
            }
            if (policy == RateLimitPolicy.DEGRADE_LOCAL_STRICT) {
                int strictLocalLimit = Math.max(1, Math.min(maxRequests, 2));
                return isAllowedLocally(key, strictLocalLimit, windowSeconds);
            }
        }
        return isAllowedLocally(key, maxRequests, windowSeconds);
    }

    private boolean isAllowedLocally(String key, int maxRequests, int windowSeconds) {
        long now = Instant.now().getEpochSecond();
        LocalCounter counter = localFallback.compute(key, (k, existing) -> {
            if (existing == null || now >= existing.expiresAt) {
                return new LocalCounter(1, now + windowSeconds);
            }
            existing.count++;
            return existing;
        });
        return counter.count <= maxRequests;
    }

    private static class LocalCounter {
        long count;
        final long expiresAt;

        LocalCounter(long count, long expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
