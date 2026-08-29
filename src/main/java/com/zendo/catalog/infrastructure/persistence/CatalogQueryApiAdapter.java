package com.zendo.catalog.infrastructure.persistence;

import com.zendo.catalog.api.CatalogQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogQueryApiAdapter implements CatalogQueryApi {

    private final SpringDataProductRepository repository;

    public CatalogQueryApiAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isProductVariantActive(String vendorId, String sku) {
        return getVariantInfo(vendorId, sku).isPresent();
    }

    @Override
    public Optional<ProductVariantInfo> getVariantInfo(String vendorId, String sku) {
        return repository.findByVendorIdAndSku(UUID.fromString(vendorId), sku)
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .flatMap(p -> p.getVariants().stream()
                        .filter(v -> v.getSku().equals(sku))
                        .findFirst()
                        .map(v -> new ProductVariantInfo(
                                p.getId().toString(),
                                p.getVendorId().toString(),
                                v.getSku(),
                                p.getName(),
                                v.getPriceAmount(),
                                v.getPriceCurrency()
                        )));
    }
}
