package com.zendo.promotion.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

public class EligibilityScope {
    private final boolean global;
    private final Set<UUID> vendorIds;
    private final Set<UUID> productIds;

    public EligibilityScope(boolean global, Set<UUID> vendorIds, Set<UUID> productIds) {
        this.global = global;
        this.vendorIds = vendorIds == null ? new HashSet<>() : new HashSet<>(vendorIds);
        this.productIds = productIds == null ? new HashSet<>() : new HashSet<>(productIds);
    }

    public static EligibilityScope global() {
        return new EligibilityScope(true, null, null);
    }

    public static EligibilityScope forVendors(Set<UUID> vendorIds) {
        return new EligibilityScope(false, vendorIds, null);
    }

    public static EligibilityScope forProducts(Set<UUID> productIds) {
        return new EligibilityScope(false, null, productIds);
    }

    public boolean isEligible(UUID vendorId, UUID productId) {
        if (global) {
            return true;
        }
        if (!vendorIds.isEmpty() && vendorIds.contains(vendorId)) {
            return true;
        }
        if (!productIds.isEmpty() && productIds.contains(productId)) {
            return true;
        }
        return false;
    }

    public boolean isGlobal() {
        return global;
    }

    public Set<UUID> getVendorIds() {
        return Collections.unmodifiableSet(vendorIds);
    }

    public Set<UUID> getProductIds() {
        return Collections.unmodifiableSet(productIds);
    }
}
