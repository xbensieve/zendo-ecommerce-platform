package com.zendo.catalog.application;

import com.zendo.catalog.domain.*;
import com.zendo.shared.messaging.EventPublisher;
import com.zendo.vendor.api.VendorQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogUseCasesTest {

    private ProductRepository productRepository;
    private VendorQueryApi vendorQueryApi;
    private EventPublisher eventPublisher;
    private CatalogUseCases catalogUseCases;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        vendorQueryApi = mock(VendorQueryApi.class);
        eventPublisher = mock(EventPublisher.class);
        catalogUseCases = new CatalogUseCases(productRepository, vendorQueryApi, eventPublisher);
    }

    @Test
    void publishProduct_ThrowsIfVendorInactive() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test"), null);
        product.addVariant(new SKU("SKU-1"), new Money(new BigDecimal("10"), "USD"));
        when(productRepository.findById(any())).thenReturn(Optional.of(product));
        when(vendorQueryApi.isVendorActive(any())).thenReturn(false);

        assertThrows(CatalogException.class, () -> catalogUseCases.publishProduct(product.getId().value().toString()));
        verify(productRepository, never()).save(any());
    }

    @Test
    void publishProduct_SucceedsIfVendorActive() {
        Product product = Product.create(VendorId.generate(), new ProductName("Test"), null);
        product.addVariant(new SKU("SKU-1"), new Money(new BigDecimal("10"), "USD"));
        when(productRepository.findById(any())).thenReturn(Optional.of(product));
        when(vendorQueryApi.isVendorActive(any())).thenReturn(true);

        catalogUseCases.publishProduct(product.getId().value().toString());

        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        verify(productRepository, times(1)).save(product);
    }
}
