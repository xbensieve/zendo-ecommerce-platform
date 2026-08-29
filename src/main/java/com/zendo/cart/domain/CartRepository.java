package com.zendo.cart.domain;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    void save(Cart cart);
    Optional<Cart> findById(UUID id);
    Optional<Cart> findActiveCartByCustomerId(String customerId);
}
