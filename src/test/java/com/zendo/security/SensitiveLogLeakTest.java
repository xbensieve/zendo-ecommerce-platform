package com.zendo.security;

import com.zendo.shared.messaging.EventSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveLogLeakTest {

    @Test
    @DisplayName("sanitizeForLog must neutralize CRLF characters to prevent log injection")
    void sanitizeForLog_mustNeutralizeCrlf() {
        String maliciousPayload = "{\"event\":\"test\"}\r\n2026-09-04 12:00:00 [INFO] Fake log entry: Admin logged in";
        String sanitized = EventSigner.sanitizeForLog(maliciousPayload);

        assertFalse(sanitized.contains("\r"), "Sanitized string must not contain carriage returns");
        assertFalse(sanitized.contains("\n"), "Sanitized string must not contain newlines");
        assertTrue(sanitized.contains("_"), "CRLF characters should be replaced with underscores");
    }

    @Test
    @DisplayName("sanitizeForLog must truncate excessive payloads to prevent log flooding / DoS")
    void sanitizeForLog_mustTruncateExcessiveLength() {
        String hugePayload = "A".repeat(5000);
        String sanitized = EventSigner.sanitizeForLog(hugePayload);

        assertTrue(sanitized.length() <= 220, "Sanitized string must be bounded in length");
        assertTrue(sanitized.endsWith("... [truncated]"));
    }

    @Test
    @DisplayName("sanitizeForLog should handle null gracefully")
    void sanitizeForLog_handlesNull() {
        assertEquals("null", EventSigner.sanitizeForLog(null));
    }
}
