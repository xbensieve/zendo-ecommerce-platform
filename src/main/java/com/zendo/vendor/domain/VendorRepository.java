package com.zendo.vendor.domain;

import java.util.Optional;

public interface VendorRepository {
    void save(Vendor vendor);
    Optional<Vendor> findById(VendorId id);
}
