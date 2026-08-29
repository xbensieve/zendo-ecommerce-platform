package com.zendo.catalog.application;

import com.zendo.catalog.domain.*;
import com.zendo.shared.messaging.EventPublisher;
import com.zendo.vendor.api.VendorQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class CatalogUseCases {

    private final ProductRepository productRepository;
    private final VendorQueryApi vendorQueryApi;
    private final EventPublisher eventPublisher;

    public CatalogUseCases(ProductRepository productRepository, VendorQueryApi vendorQueryApi, EventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.vendorQueryApi = vendorQueryApi;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public String createProduct(String vendorId, String name, String description) {
        Product product = Product.create(
            VendorId.fromString(vendorId), 
            new ProductName(name), 
            description != null ? new ProductDescription(description) : null
        );
        productRepository.save(product);
        product.getDomainEvents().forEach(eventPublisher::publish);
        product.clearDomainEvents();
        return product.getId().value().toString();
    }

    @Transactional
    public String addVariant(String productId, String sku, BigDecimal priceAmount, String currency) {
        Product product = productRepository.findById(ProductId.fromString(productId))
            .orElseThrow(() -> new CatalogException("Product not found"));
        product.addVariant(new SKU(sku), new Money(priceAmount, currency));
        productRepository.save(product);
        product.getDomainEvents().forEach(eventPublisher::publish);
        product.clearDomainEvents();
        return product.getVariants().get(product.getVariants().size() - 1).getId().value().toString();
    }

    @Transactional
    public void publishProduct(String productId) {
        Product product = productRepository.findById(ProductId.fromString(productId))
            .orElseThrow(() -> new CatalogException("Product not found"));
        
        if (!vendorQueryApi.isVendorActive(product.getVendorId().value().toString())) {
            throw new CatalogException("Cannot publish product for inactive vendor");
        }
        
        product.activate();
        productRepository.save(product);
        product.getDomainEvents().forEach(eventPublisher::publish);
        product.clearDomainEvents();
    }

    @Transactional
    public void deactivateProduct(String productId) {
        Product product = productRepository.findById(ProductId.fromString(productId))
            .orElseThrow(() -> new CatalogException("Product not found"));
        product.deactivate();
        productRepository.save(product);
        product.getDomainEvents().forEach(eventPublisher::publish);
        product.clearDomainEvents();
    }
    
    @Transactional
    public void archiveProduct(String productId) {
        Product product = productRepository.findById(ProductId.fromString(productId))
            .orElseThrow(() -> new CatalogException("Product not found"));
        product.archive();
        productRepository.save(product);
        product.getDomainEvents().forEach(eventPublisher::publish);
        product.clearDomainEvents();
    }
}
