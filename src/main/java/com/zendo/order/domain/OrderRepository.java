package com.zendo.order.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(ParentOrder order);
    Optional<ParentOrder> findById(UUID id);
    boolean checkAndSaveIdempotencyKey(String idempotencyKey);
    boolean hasPaidOrderItem(String customerId, UUID orderItemId);
}
