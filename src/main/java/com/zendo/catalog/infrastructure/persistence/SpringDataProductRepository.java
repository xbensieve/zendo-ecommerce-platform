package com.zendo.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SpringDataProductRepository extends JpaRepository<ProductEntity, UUID> {
    
    @Query("SELECT p FROM ProductEntity p JOIN p.variants v WHERE p.vendorId = :vendorId AND v.sku = :sku")
    Optional<ProductEntity> findByVendorIdAndSku(@Param("vendorId") UUID vendorId, @Param("sku") String sku);
}
