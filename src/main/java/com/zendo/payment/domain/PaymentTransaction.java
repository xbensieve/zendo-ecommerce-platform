package com.zendo.payment.domain;

import com.zendo.payment.domain.events.PaymentAuthorized;
import com.zendo.payment.domain.events.PaymentCaptured;
import com.zendo.payment.domain.events.PaymentFailed;
import com.zendo.shared.messaging.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PaymentTransaction {
    private final UUID id;
    private final UUID orderId;
    private final String customerId;
    private final BigDecimal amount;
    private final String currency;
    private String gatewayReference;
    private PaymentStatus status;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Reconstitution constructor
    public PaymentTransaction(UUID id, UUID orderId, String customerId, BigDecimal amount, String currency, String gatewayReference, PaymentStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.gatewayReference = gatewayReference;
        this.status = status;
    }

    public static PaymentTransaction createNew(UUID orderId, String customerId, BigDecimal amount, String currency) {
        return new PaymentTransaction(UUID.randomUUID(), orderId, customerId, amount, currency, null, PaymentStatus.PENDING);
    }

    public void authorize(PaymentGatewayPort gateway) {
        if (this.status != PaymentStatus.PENDING) {
            throw new PaymentException("Only PENDING payments can be authorized.");
        }

        var response = gateway.authorize(this.orderId, this.amount, this.currency);
        this.gatewayReference = response.gatewayReference();

        if (response.success()) {
            this.status = PaymentStatus.AUTHORIZED;
            registerEvent(new PaymentAuthorized(UUID.randomUUID(), Instant.now(), this.id.toString(), this.orderId.toString(), this.customerId, this.amount, this.currency));
        } else {
            this.status = PaymentStatus.FAILED;
            registerEvent(new PaymentFailed(UUID.randomUUID(), Instant.now(), this.id.toString(), this.orderId.toString(), this.customerId, response.errorMessage()));
        }
    }

    public void capture(PaymentGatewayPort gateway) {
        if (this.status != PaymentStatus.AUTHORIZED) {
            throw new PaymentException("Only AUTHORIZED payments can be captured.");
        }
        
        var response = gateway.capture(this.gatewayReference);
        if (response.success()) {
            this.status = PaymentStatus.CAPTURED;
            registerEvent(new PaymentCaptured(UUID.randomUUID(), Instant.now(), this.id.toString(), this.orderId.toString()));
        } else {
            // Note: Capture failures usually don't mark the payment as FAILED immediately, it might need manual intervention
            // but for simplicity we'll just throw an exception.
            throw new PaymentException("Capture failed: " + response.errorMessage());
        }
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getGatewayReference() { return gatewayReference; }
    public PaymentStatus getStatus() { return status; }
    
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }
}
