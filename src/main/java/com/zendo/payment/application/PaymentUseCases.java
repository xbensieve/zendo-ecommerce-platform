package com.zendo.payment.application;

import com.zendo.payment.domain.PaymentException;
import com.zendo.payment.domain.PaymentGatewayPort;
import com.zendo.payment.domain.PaymentRepository;
import com.zendo.payment.domain.PaymentTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentUseCases {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;

    public PaymentUseCases(PaymentRepository paymentRepository, PaymentGatewayPort paymentGatewayPort) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayPort = paymentGatewayPort;
    }

    @Transactional
    public void authorizePayment(String orderId, String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        if (!paymentRepository.checkAndSaveIdempotencyKey(idempotencyKey)) {
            // Idempotent: already processed
            return;
        }

        UUID orderUuid = UUID.fromString(orderId);
        
        // Prevent duplicate successful payment transactions for the same order
        var existing = paymentRepository.findByOrderId(orderUuid);
        if (existing.isPresent()) {
            throw new PaymentException("Payment transaction already exists for order: " + orderId);
        }

        PaymentTransaction transaction = PaymentTransaction.createNew(orderUuid, customerId, amount, currency);
        
        transaction.authorize(paymentGatewayPort);
        
        paymentRepository.save(transaction);
    }
}
