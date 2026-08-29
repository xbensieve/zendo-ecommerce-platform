package com.zendo.vendor.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Vendor {
    private final VendorId id;
    private VendorName name;
    private VendorStatus status;
    private String ownerUserId;
    private final List<DomainEvent> domainEvents;

    // For reconstitution from DB
    private Vendor(VendorId id, VendorName name, VendorStatus status, String ownerUserId) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.ownerUserId = ownerUserId;
        this.domainEvents = new ArrayList<>();
    }

    public static Vendor reconstitute(VendorId id, VendorName name, VendorStatus status, String ownerUserId) {
        return new Vendor(id, name, status, ownerUserId);
    }

    // New Vendor Onboarding
    public static Vendor onboard(VendorName name, String ownerUserId) {
        Vendor vendor = new Vendor(VendorId.generate(), name, VendorStatus.PENDING, ownerUserId);
        vendor.registerEvent(new VendorOnboarded(UUID.randomUUID(), Instant.now(), vendor.getId().value().toString(), name.value()));
        return vendor;
    }

    public void activate() {
        if (this.status != VendorStatus.PENDING && this.status != VendorStatus.SUSPENDED) {
            throw new VendorException("Vendor can only be activated from PENDING or SUSPENDED state. Current state: " + this.status);
        }
        this.status = VendorStatus.ACTIVE;
        this.registerEvent(new VendorActivated(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public void suspend() {
        if (this.status != VendorStatus.ACTIVE) {
            throw new VendorException("Only ACTIVE vendors can be suspended.");
        }
        this.status = VendorStatus.SUSPENDED;
        this.registerEvent(new VendorSuspended(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public void deactivate() {
        if (this.status != VendorStatus.ACTIVE && this.status != VendorStatus.SUSPENDED) {
            throw new VendorException("Only ACTIVE or SUSPENDED vendors can be deactivated.");
        }
        this.status = VendorStatus.DEACTIVATED;
        this.registerEvent(new VendorDeactivated(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public VendorId getId() {
        return id;
    }

    public VendorName getName() {
        return name;
    }

    public VendorStatus getStatus() {
        return status;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

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
