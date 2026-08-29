package com.zendo.promotion.infrastructure.persistence;

import com.zendo.promotion.domain.Coupon;
import com.zendo.promotion.domain.CouponRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    public CouponRepositoryImpl(CouponJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Coupon coupon) {
        // JPA will use optimistic locking based on the @Version field when we update.
        // Wait, CouponJpaEntity needs the version field to be set from the domain if we want it to map correctly.
        // But since we just load it, call redeem(), and save it in the same transaction, Spring Data JPA 
        // will handle version checking if we fetched the entity in the same transaction.
        // However, since we map to Domain and back to Entity, we lose the JPA managed state.
        // So we need to ensure the @Version field is passed through the domain model or we use findById, 
        // modify the entity and save.
        
        // Given we are mapping Domain -> Entity, we must pass the version field.
        // I will need to update Coupon and PromotionMapper to include the version field.
        jpaRepository.save(PromotionMapper.toEntity(coupon));
    }

    @Override
    public Optional<Coupon> findById(UUID id) {
        return jpaRepository.findById(id).map(PromotionMapper::toDomain);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return jpaRepository.findByCode(code).map(PromotionMapper::toDomain);
    }
}
