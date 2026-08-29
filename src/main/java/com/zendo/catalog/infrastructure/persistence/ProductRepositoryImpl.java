package com.zendo.catalog.infrastructure.persistence;

import com.zendo.catalog.domain.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final SpringDataProductRepository jpaRepository;

    public ProductRepositoryImpl(SpringDataProductRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Product product) {
        ProductEntity entity = new ProductEntity(
            product.getId().value(),
            product.getVendorId().value(),
            product.getName().value(),
            product.getDescription() != null ? product.getDescription().value() : null,
            product.getStatus().name()
        );

        for (ProductVariant variant : product.getVariants()) {
            ProductVariantEntity variantEntity = new ProductVariantEntity(
                variant.getId().value(),
                product.getVendorId().value(),
                variant.getSku().value(),
                variant.getPrice().amount(),
                variant.getPrice().currency()
            );
            entity.addVariant(variantEntity);
        }

        jpaRepository.save(entity);
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return jpaRepository.findById(id.value())
            .map(entity -> {
                List<ProductVariant> variants = entity.getVariants().stream()
                    .map(v -> new ProductVariant(
                        ProductVariantId.fromString(v.getId().toString()),
                        new SKU(v.getSku()),
                        new Money(v.getPriceAmount(), v.getPriceCurrency())
                    )).collect(Collectors.toList());

                return Product.reconstitute(
                    ProductId.fromString(entity.getId().toString()),
                    VendorId.fromString(entity.getVendorId().toString()),
                    new ProductName(entity.getName()),
                    entity.getDescription() != null ? new ProductDescription(entity.getDescription()) : null,
                    ProductStatus.valueOf(entity.getStatus()),
                    variants
                );
            });
    }
}
