package com.zendo.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {
    GatewayResponse authorize(UUID orderId, BigDecimal amount, String currency);
    GatewayResponse capture(String gatewayReference);
    GatewayResponse refund(String gatewayReference);

    record GatewayResponse(boolean success, String gatewayReference, String errorCode, String errorMessage) {}
}
