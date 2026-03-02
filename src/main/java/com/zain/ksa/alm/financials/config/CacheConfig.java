package com.zain.ksa.alm.financials.config;


import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-memory cache configuration.
 *
 * Caffeine is a high-performance, near-optimal caching library.
 * It uses the Window TinyLFU eviction policy which outperforms LRU
 * in real-world access patterns.
 *
 * Replaces Redis with zero infrastructure requirements.
 * When Redis becomes available, only this file needs to change.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Per-cache spec strings follow Caffeine's builder syntax:
     *   initialCapacity  — pre-allocate buckets (avoids resize cost)
     *   maximumSize      — evict LFU entries when limit reached
     *   expireAfterWrite — TTL from time of write
     *   recordStats      — enables hit/miss metrics via Actuator
     */
    private static final Map<String, String> CACHE_SPECS = Map.of(
        // Financial FAR data — slow to change, larger dataset
        "depreciation:list",    "initialCapacity=50,maximumSize=500,expireAfterWrite=5m,recordStats",
        "depreciation:single",  "initialCapacity=100,maximumSize=2000,expireAfterWrite=5m,recordStats",

        // Active/Passive inventory — moderate change rate
        "active-inventory:list",    "initialCapacity=50,maximumSize=300,expireAfterWrite=3m,recordStats",
        "active-inventory:single",  "initialCapacity=100,maximumSize=1000,expireAfterWrite=3m,recordStats",
        "passive-inventory:list",   "initialCapacity=50,maximumSize=300,expireAfterWrite=3m,recordStats",
        "passive-inventory:single", "initialCapacity=100,maximumSize=1000,expireAfterWrite=3m,recordStats",

        // Unmapped — refreshed nightly, short TTL forces re-read after scheduler
        "unmapped-active:list",  "initialCapacity=20,maximumSize=200,expireAfterWrite=1m,recordStats",
        "unmapped-passive:list", "initialCapacity=20,maximumSize=200,expireAfterWrite=1m,recordStats",
        "unmapped-it:list",      "initialCapacity=20,maximumSize=200,expireAfterWrite=1m,recordStats"
    );

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Register each cache with its own spec
        CACHE_SPECS.forEach((cacheName, spec) ->
            manager.registerCustomCache(cacheName,
                Caffeine.from(spec).build())
        );

        // Fallback spec for any cache name not explicitly configured
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats()
        );

        // Do NOT cache null values — prevents empty-result poisoning
        manager.setAllowNullValues(false);

        return manager;
    }
}