package com.zendo.catalog.api;

import java.math.BigDecimal;
import java.util.Optional;

public interface CatalogQueryApi {
    boolean isProductVariantActive(String vendorId, String sku);
    Optional<ProductVariantInfo> getVariantInfo(String vendorId, String sku);

    record ProductVariantInfo(
            String productId,
            String vendorId,
            String sku,
            String productName,
            BigDecimal priceAmount,
            String priceCurrency
    ) {}
}
