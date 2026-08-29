package com.zendo.promotion.api;

import com.zendo.promotion.domain.Coupon;
import com.zendo.promotion.domain.CouponRepository;
import com.zendo.promotion.domain.PromotionException;
import org.springframework.stereotype.Service;

@Service
public class PromotionApiImpl implements PromotionApi {

    private final CouponRepository couponRepository;
    private final com.zendo.promotion.domain.FlashSaleRepository flashSaleRepository;

    public PromotionApiImpl(CouponRepository couponRepository, com.zendo.promotion.domain.FlashSaleRepository flashSaleRepository) {
        this.couponRepository = couponRepository;
        this.flashSaleRepository = flashSaleRepository;
    }

    @Override
    public void redeemCoupon(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return;
        }

        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new PromotionException("Coupon not found: " + couponCode));
        
        coupon.redeem();
        couponRepository.save(coupon);
    }

    @Override
    public boolean reserveFlashSaleAllocation(java.util.UUID flashSaleId, int quantity) {
        return flashSaleRepository.atomicReserveAllocation(flashSaleId, quantity);
    }

    @Override
    public java.util.Optional<FlashSaleDetails> getFlashSale(java.util.UUID flashSaleId) {
        return flashSaleRepository.findById(flashSaleId)
                .map(fs -> new FlashSaleDetails(
                        fs.getId(),
                        fs.getVendorId(),
                        fs.getProductId(),
                        fs.getSku(),
                        fs.getFlashPrice()
                ));
    }
}

