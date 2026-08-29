package com.zendo.cart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataCartRepository extends JpaRepository<CartEntity, UUID> {
    Optional<CartEntity> findByCustomerIdAndStatus(String customerId, String status);
}
