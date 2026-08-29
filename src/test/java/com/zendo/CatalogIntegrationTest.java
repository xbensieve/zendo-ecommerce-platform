package com.zendo;

import com.zendo.catalog.application.CatalogUseCases;
import com.zendo.catalog.domain.ProductId;
import com.zendo.catalog.domain.ProductRepository;
import com.zendo.catalog.domain.ProductStatus;
import com.zendo.vendor.application.VendorUseCases;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CatalogUseCases catalogUseCases;

    @Autowired
    private VendorUseCases vendorUseCases;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void testCreateAndPublishProduct() {
        // 1. Create active vendor
        String vendorId = vendorUseCases.onboardVendor("Acme Electronics", "owner_123");
        vendorUseCases.activateVendor(vendorId);

        // 2. Create product
        String productIdStr = catalogUseCases.createProduct(vendorId, "Integration Test Product", "Desc");
        assertNotNull(productIdStr);
        
        // 3. Add variant
        catalogUseCases.addVariant(productIdStr, "SKU-INT-1", new BigDecimal("49.99"), "USD");

        // 4. Publish
        catalogUseCases.publishProduct(productIdStr);

        var product = productRepository.findById(ProductId.fromString(productIdStr)).orElseThrow();
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        assertEquals(1, product.getVariants().size());
        assertEquals("SKU-INT-1", product.getVariants().get(0).getSku().value());
    }
}
