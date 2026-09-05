package com.zendo.vendor.application;

import com.zendo.shared.messaging.EventPublisher;
import com.zendo.vendor.domain.Vendor;
import com.zendo.vendor.domain.VendorId;
import com.zendo.vendor.domain.VendorName;
import com.zendo.vendor.domain.VendorRepository;
import com.zendo.vendor.domain.VendorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorUseCases {

    private final VendorRepository vendorRepository;
    private final EventPublisher eventPublisher;
    private final com.zendo.security.api.SecurityCommandApi securityCommandApi;

    @org.springframework.beans.factory.annotation.Autowired
    public VendorUseCases(VendorRepository vendorRepository, EventPublisher eventPublisher, com.zendo.security.api.SecurityCommandApi securityCommandApi) {
        this.vendorRepository = vendorRepository;
        this.eventPublisher = eventPublisher;
        this.securityCommandApi = securityCommandApi;
    }

    public VendorUseCases(VendorRepository vendorRepository, EventPublisher eventPublisher) {
        this(vendorRepository, eventPublisher, null);
    }

    @Transactional
    public String onboardVendor(String name, String ownerUserId) {
        Vendor vendor = Vendor.onboard(new VendorName(name), ownerUserId);
        vendorRepository.save(vendor);
        vendor.getDomainEvents().forEach(eventPublisher::publish);
        vendor.clearDomainEvents();
        return vendor.getId().value().toString();
    }

    @Transactional
    public void activateVendor(String vendorId) {
        Vendor vendor = vendorRepository.findById(VendorId.fromString(vendorId))
            .orElseThrow(() -> new VendorException("Vendor not found"));
        vendor.activate();
        vendorRepository.save(vendor);
        if (securityCommandApi != null) {
            securityCommandApi.upgradeToVendorIfCustomer(vendor.getOwnerUserId());
        }
        vendor.getDomainEvents().forEach(eventPublisher::publish);
        vendor.clearDomainEvents();
    }

    @Transactional
    public void suspendVendor(String vendorId) {
        Vendor vendor = vendorRepository.findById(VendorId.fromString(vendorId))
            .orElseThrow(() -> new VendorException("Vendor not found"));
        vendor.suspend();
        vendorRepository.save(vendor);
        vendor.getDomainEvents().forEach(eventPublisher::publish);
        vendor.clearDomainEvents();
    }

    @Transactional
    public void deactivateVendor(String vendorId) {
        Vendor vendor = vendorRepository.findById(VendorId.fromString(vendorId))
            .orElseThrow(() -> new VendorException("Vendor not found"));
        vendor.deactivate();
        vendorRepository.save(vendor);
        vendor.getDomainEvents().forEach(eventPublisher::publish);
        vendor.clearDomainEvents();
    }
}
