package com.cornercrew.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.geolocation")
public class GeoLocationProperties {

    private String provider;
    private String apiKey;
    private long intersectionCacheTtlHours = 24;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getIntersectionCacheTtlHours() {
        return intersectionCacheTtlHours;
    }

    public void setIntersectionCacheTtlHours(long intersectionCacheTtlHours) {
        this.intersectionCacheTtlHours = intersectionCacheTtlHours;
    }
}
