package com.zendo.security;

import com.zendo.promotion.api.PromotionApi;
import com.zendo.promotion.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class CouponConcurrentAbuseTest {

    @Autowired
    private PromotionApi promotionApi;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("Concurrent Coupon Redemption: 10 parallel redemptions on maxUses=1 coupon must allow exactly ONE success")
    void concurrentRedemption_singleUseCoupon_succeedsExactlyOnce() throws Exception {
        String couponCode = "ONETIME-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant now = Instant.now();

        Coupon coupon = new Coupon(
                UUID.randomUUID(),
                couponCode,
                now.minus(1, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.DAYS),
                new DiscountRule(DiscountType.FIXED_AMOUNT, new BigDecimal("10.00")),
                new EligibilityScope(true, null, null),
                1 // Single use only!
        );
        couponRepository.save(coupon);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    promotionApi.redeemCoupon(couponCode);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 10 threads concurrently

        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successes.get(), "Exactly one concurrent thread must succeed in redeeming single-use coupon");
        assertEquals(threads - 1, failures.get(), "All other concurrent attempts must be rejected");

        // Verify state in database
        Coupon updated = couponRepository.findByCode(couponCode).orElseThrow();
        assertEquals(1, updated.getCurrentUses(), "Coupon currentUses must not exceed maxUses (1)");
    }
}
