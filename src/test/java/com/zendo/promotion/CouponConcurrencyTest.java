package com.zendo.promotion;

import com.zendo.promotion.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class CouponConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private com.zendo.promotion.api.PromotionApi promotionApi;

    @Test
    void concurrentCouponRedemptionShouldEnforceLimit() throws InterruptedException {
        // Create a coupon with max Uses = 2
        Coupon coupon = new Coupon(
                UUID.randomUUID(),
                "FLASH50",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS),
                new DiscountRule(DiscountType.PERCENTAGE, new BigDecimal("50")),
                EligibilityScope.global(),
                2
        );
        couponRepository.save(coupon);

        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        AtomicInteger promotionFailureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                boolean success = false;
                int retries = 0;
                while (!success && retries < 10) {
                    try {
                        promotionApi.redeemCoupon("FLASH50");
                        successCount.incrementAndGet();
                        success = true;
                    } catch (PromotionException e) {
                        promotionFailureCount.incrementAndGet();
                        break; // Domain exception means limit reached
                    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                        retries++;
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    } catch (Exception e) {
                        System.out.println("Other exception: " + e.getMessage());
                        break;
                    }
                }
                latch.countDown();
            });
        }

        latch.await();
        executorService.shutdown();

        Coupon updatedCoupon = couponRepository.findByCode("FLASH50").orElseThrow();

        assertEquals(2, updatedCoupon.getCurrentUses(), "Coupon should only be used 2 times");
        assertEquals(2, successCount.get(), "Only 2 successful redemptions should occur");
    }
}
