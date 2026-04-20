package com.cornercrew.app.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for the CornerCrew platform.
 * 
 * <p>Configures Caffeine as the cache provider with TTL-based expiration
 * for geolocation intersection data.
 */
@Configuration
public class CacheConfig {

    private final GeoLocationProperties geoLocationProperties;

    public CacheConfig(GeoLocationProperties geoLocationProperties) {
        this.geoLocationProperties = geoLocationProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("intersections");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(geoLocationProperties.getIntersectionCacheTtlHours(), TimeUnit.HOURS)
                .maximumSize(100)
                .recordStats();
    }
}
