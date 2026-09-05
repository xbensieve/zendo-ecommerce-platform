package com.zendo.vendor.api;

import java.util.Optional;

public interface VendorQueryApi {
    boolean isVendorActive(String vendorId);
    boolean isVendorOwner(String vendorId, String userId);
    Optional<String> getVendorOwnerUserId(String vendorId);
}
