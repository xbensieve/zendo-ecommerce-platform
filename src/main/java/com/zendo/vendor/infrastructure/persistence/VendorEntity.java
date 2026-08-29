package com.zendo.vendor.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendors", schema = "vendor")
public class VendorEntity {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String status;
    
    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected VendorEntity() {}

    public VendorEntity(UUID id, String name, String status, String ownerUserId) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.ownerUserId = ownerUserId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }
}
