package com.zendo.vendor.application;

import com.zendo.vendor.api.VendorQueryApi;
import com.zendo.vendor.domain.Vendor;
import com.zendo.vendor.domain.VendorId;
import com.zendo.vendor.domain.VendorStatus;
import com.zendo.vendor.domain.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorQueryApiImpl implements VendorQueryApi {

    private final VendorRepository vendorRepository;

    public VendorQueryApiImpl(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVendorActive(String vendorId) {
        return vendorRepository.findById(VendorId.fromString(vendorId))
            .map(Vendor::getStatus)
            .map(status -> status == VendorStatus.ACTIVE)
            .orElse(false);
    }
}
