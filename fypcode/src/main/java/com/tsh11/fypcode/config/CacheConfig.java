package com.tsh11.fypcode.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

//Cache, how long they expire, size limit

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache latestPriceCache = new CaffeineCache(
                "latestPrice",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache latestPriceBatchCache = new CaffeineCache(
                "latestPriceBatch",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build()
        );

        CaffeineCache historicalPriceCache = new CaffeineCache(
                "historicalPrice",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache historicalIndexPriceCache = new CaffeineCache(
                "historicalIndexPrice",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .build()
        );

        CaffeineCache assetSearchCache = new CaffeineCache(
                "assetSearch",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(500)
                        .build()
        );

        CaffeineCache llmExplanationCache = new CaffeineCache(
                "llmExplanation",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(500)
                        .build()
        );

        CaffeineCache holdingsByUserCache = new CaffeineCache(
                "holdingsByUser",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build()
        );

        CaffeineCache assetMetadataCache = new CaffeineCache(
                "assetMetadata",
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .build()
        );

        cacheManager.setCaches(List.of(
                latestPriceCache,
                latestPriceBatchCache,
                historicalPriceCache,
                historicalIndexPriceCache,
                assetSearchCache,
                llmExplanationCache,
                holdingsByUserCache,
                assetMetadataCache
        ));

        return cacheManager;
    }
}