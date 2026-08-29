package com.zendo.catalog.domain;

import com.zendo.shared.messaging.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Product {
    private final ProductId id;
    private final VendorId vendorId;
    private ProductName name;
    private ProductDescription description;
    private ProductStatus status;
    private final List<ProductVariant> variants;
    private final List<DomainEvent> domainEvents;

    private Product(ProductId id, VendorId vendorId, ProductName name, ProductDescription description, ProductStatus status, List<ProductVariant> variants) {
        this.id = id;
        this.vendorId = vendorId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.variants = variants;
        this.domainEvents = new ArrayList<>();
    }

    public static Product reconstitute(ProductId id, VendorId vendorId, ProductName name, ProductDescription description, ProductStatus status, List<ProductVariant> variants) {
        return new Product(id, vendorId, name, description, status, new ArrayList<>(variants));
    }

    public static Product create(VendorId vendorId, ProductName name, ProductDescription description) {
        Product product = new Product(ProductId.generate(), vendorId, name, description, ProductStatus.DRAFT, new ArrayList<>());
        product.registerEvent(new ProductCreated(UUID.randomUUID(), Instant.now(), product.getId().value().toString(), vendorId.value().toString()));
        return product;
    }

    public void addVariant(SKU sku, Money price) {
        if (this.status == ProductStatus.ARCHIVED) {
            throw new CatalogException("Cannot add variant to an archived product");
        }
        ProductVariant variant = new ProductVariant(ProductVariantId.generate(), sku, price);
        this.variants.add(variant);
        this.registerEvent(new ProductVariantAdded(UUID.randomUUID(), Instant.now(), this.id.value().toString(), variant.getId().value().toString(), sku.value()));
    }

    public void activate() {
        if (this.status == ProductStatus.ARCHIVED) {
            throw new CatalogException("Cannot activate an archived product");
        }
        if (this.name == null) {
            throw new CatalogException("Product must have a name to be activated");
        }
        if (this.variants.isEmpty()) {
            throw new CatalogException("Product must have at least one variant to be activated");
        }
        for (ProductVariant variant : variants) {
            if (!variant.isValidForPublishing()) {
                throw new CatalogException("Variant " + variant.getSku().value() + " has an invalid price for publishing");
            }
        }
        this.status = ProductStatus.ACTIVE;
        this.registerEvent(new ProductPublished(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public void deactivate() {
        if (this.status != ProductStatus.ACTIVE) {
            throw new CatalogException("Only active products can be deactivated");
        }
        this.status = ProductStatus.INACTIVE;
        this.registerEvent(new ProductDeactivated(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public void archive() {
        if (this.status == ProductStatus.ARCHIVED) {
            return;
        }
        this.status = ProductStatus.ARCHIVED;
        this.registerEvent(new ProductArchived(UUID.randomUUID(), Instant.now(), this.id.value().toString()));
    }

    public void updateInformation(ProductName name, ProductDescription description) {
        if (this.status == ProductStatus.ARCHIVED) {
            throw new CatalogException("Cannot update an archived product");
        }
        this.name = name;
        this.description = description;
    }

    public ProductId getId() { return id; }
    public VendorId getVendorId() { return vendorId; }
    public ProductName getName() { return name; }
    public ProductDescription getDescription() { return description; }
    public ProductStatus getStatus() { return status; }
    public List<ProductVariant> getVariants() { return Collections.unmodifiableList(variants); }

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
