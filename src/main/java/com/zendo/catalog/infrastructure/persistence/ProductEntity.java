package com.zendo.catalog.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "catalog")
public class ProductEntity {
    
    @Id
    private UUID id;
    
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    
    @Column(nullable = false)
    private String name;
    
    @Column
    private String description;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariantEntity> variants = new ArrayList<>();

    protected ProductEntity() {}

    public ProductEntity(UUID id, UUID vendorId, String name, String description, String status) {
        this.id = id;
        this.vendorId = vendorId;
        this.name = name;
        this.description = description;
        this.status = status;
    }

    public void addVariant(ProductVariantEntity variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public UUID getId() { return id; }
    public UUID getVendorId() { return vendorId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public List<ProductVariantEntity> getVariants() { return variants; }
}
