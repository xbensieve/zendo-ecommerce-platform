package com.zendo.shared.config;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimitingCacheConfig {

    @Bean
    public CacheManager jCacheManager() {
        var provider = Caching.getCachingProvider(CaffeineCachingProvider.class.getName());
        CacheManager cacheManager = provider.getCacheManager();

        MutableConfiguration<String, Object> config = new MutableConfiguration<String, Object>()
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 5)));

        if (cacheManager.getCache("rate-limit-auth") == null) {
            cacheManager.createCache("rate-limit-auth", config);
        }
        if (cacheManager.getCache("rate-limit-checkout") == null) {
            cacheManager.createCache("rate-limit-checkout", config);
        }
        return cacheManager;
    }
}
