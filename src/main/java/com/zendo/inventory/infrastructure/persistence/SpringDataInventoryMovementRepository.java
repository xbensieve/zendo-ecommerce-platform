package com.zendo.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataInventoryMovementRepository extends JpaRepository<InventoryMovementEntity, UUID> {
}
