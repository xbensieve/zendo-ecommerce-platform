package com.zendo.payment.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    void save(PaymentTransaction payment);
    Optional<PaymentTransaction> findById(UUID id);
    Optional<PaymentTransaction> findByOrderId(UUID orderId);
    
    boolean checkAndSaveIdempotencyKey(String key);
}
