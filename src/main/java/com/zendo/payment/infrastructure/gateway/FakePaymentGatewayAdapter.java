package com.zendo.payment.infrastructure.gateway;

import com.zendo.payment.domain.PaymentGatewayPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public GatewayResponse authorize(UUID orderId, BigDecimal amount, String currency) {
        // Deterministic mock behavior for testing:
        // Fail if amount == 0
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return new GatewayResponse(false, null, "INVALID_AMOUNT", "Amount must be greater than zero.");
        }
        // Fail if amount is negative
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new GatewayResponse(false, null, "NEGATIVE_AMOUNT", "Amount cannot be negative.");
        }
        
        // Success
        String fakeRef = "FAKE_AUTH_" + UUID.randomUUID().toString();
        return new GatewayResponse(true, fakeRef, null, null);
    }

    @Override
    public GatewayResponse capture(String gatewayReference) {
        if (gatewayReference == null || gatewayReference.isEmpty()) {
            return new GatewayResponse(false, null, "INVALID_REF", "Gateway reference is required.");
        }
        return new GatewayResponse(true, gatewayReference, null, null);
    }

    @Override
    public GatewayResponse refund(String gatewayReference) {
        if (gatewayReference == null || gatewayReference.isEmpty()) {
            return new GatewayResponse(false, null, "INVALID_REF", "Gateway reference is required.");
        }
        return new GatewayResponse(true, gatewayReference, null, null);
    }
}
