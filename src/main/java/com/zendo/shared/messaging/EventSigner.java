package com.zendo.shared.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class EventSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final java.util.Set<String> EXACT_INSECURE_SECRETS = java.util.Set.of(
            "changeme", "secret", "password", "default", "admin", "test"
    );
    private static final java.util.List<String> INSECURE_SUBSTRINGS = java.util.List.of(
            "internal-event-signing-key",
            "dev-secret",
            "default-secret",
            "example-secret"
    );

    private final byte[] secretKeyBytes;

    @org.springframework.beans.factory.annotation.Autowired
    public EventSigner(
            @Value("${zendo.security.event.signing-secret}") String secretKey,
            @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.core.env.Environment environment) {
        validateSecret(secretKey, environment);
        this.secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public EventSigner(String secretKey) {
        this(secretKey, null);
    }

    private void validateSecret(String secretKey, org.springframework.core.env.Environment environment) {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Event signing secret must be provided and cannot be empty");
        }
        if (secretKey.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "Event signing secret must have at least " + MIN_SECRET_LENGTH + " characters for HMAC-SHA256 security"
            );
        }
        if (environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            for (String exact : EXACT_INSECURE_SECRETS) {
                if (secretKey.equalsIgnoreCase(exact)) {
                    throw new IllegalStateException("Production environment detected: Insecure default or development event signing secret is strictly forbidden");
                }
            }
            for (String substr : INSECURE_SUBSTRINGS) {
                if (secretKey.toLowerCase().contains(substr.toLowerCase())) {
                    throw new IllegalStateException("Production environment detected: Insecure default or development event signing secret is strictly forbidden");
                }
            }
        }
    }

    public static String sanitizeForLog(String input) {
        if (input == null) return "null";
        String clean = input.replace('\r', '_').replace('\n', '_');
        if (clean.length() > 200) {
            return clean.substring(0, 200) + "... [truncated]";
        }
        return clean;
    }

    public static void validateFreshness(java.time.Instant occurredAt, long maxAgeSeconds, long maxFutureSkewSeconds) {
        if (occurredAt == null) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Missing required occurredAt timestamp");
        }
        java.time.Instant now = java.time.Instant.now();
        if (occurredAt.isAfter(now.plusSeconds(maxFutureSkewSeconds))) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Event timestamp is in the future beyond clock skew tolerance: " + occurredAt);
        }
        if (occurredAt.isBefore(now.minusSeconds(maxAgeSeconds))) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Event timestamp is expired beyond freshness window: " + occurredAt);
        }
    }

    public String sign(String payload) {
        if (payload == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC event signature", e);
        }
    }

    public boolean verify(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            String expected = sign(payload);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
