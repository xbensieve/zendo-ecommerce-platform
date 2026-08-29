package com.zendo.catalog.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void createProduct_CreatesDraftProduct() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test Product"), null);
        assertEquals(ProductStatus.DRAFT, product.getStatus());
        assertEquals("Test Product", product.getName().value());
        assertEquals(1, product.getDomainEvents().size());
    }

    @Test
    void activate_ThrowsExceptionIfNoVariants() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test Product"), null);
        assertThrows(CatalogException.class, product::activate);
    }

    @Test
    void activate_SucceedsWithValidVariant() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test Product"), null);
        product.addVariant(new SKU("SKU-123"), new Money(new BigDecimal("10.00"), "USD"));
        product.activate();
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }

    @Test
    void addVariant_ThrowsIfArchived() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test Product"), null);
        product.archive();
        assertThrows(CatalogException.class, () -> product.addVariant(new SKU("SKU-123"), new Money(new BigDecimal("10.00"), "USD")));
    }
}
