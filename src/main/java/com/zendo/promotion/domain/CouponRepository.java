package com.zendo.promotion.domain;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository {
    void save(Coupon coupon);
    Optional<Coupon> findById(UUID id);
    Optional<Coupon> findByCode(String code);
}
