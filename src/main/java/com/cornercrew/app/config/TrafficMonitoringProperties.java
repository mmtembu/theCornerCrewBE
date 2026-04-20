package com.cornercrew.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.traffic")
public class TrafficMonitoringProperties {

    private String provider;
    private String apiKey;
    private long pollingIntervalMs = 300000;
    private double congestionThreshold = 0.7;
    private Neighborhood neighborhood = new Neighborhood();

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

    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public void setPollingIntervalMs(long pollingIntervalMs) {
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public double getCongestionThreshold() {
        return congestionThreshold;
    }

    public void setCongestionThreshold(double congestionThreshold) {
        this.congestionThreshold = congestionThreshold;
    }

    public Neighborhood getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(Neighborhood neighborhood) {
        this.neighborhood = neighborhood;
    }

    public static class Neighborhood {

        private double southLat;
        private double westLng;
        private double northLat;
        private double eastLng;

        public double getSouthLat() {
            return southLat;
        }

        public void setSouthLat(double southLat) {
            this.southLat = southLat;
        }

        public double getWestLng() {
            return westLng;
        }

        public void setWestLng(double westLng) {
            this.westLng = westLng;
        }

        public double getNorthLat() {
            return northLat;
        }

        public void setNorthLat(double northLat) {
            this.northLat = northLat;
        }

        public double getEastLng() {
            return eastLng;
        }

        public void setEastLng(double eastLng) {
            this.eastLng = eastLng;
        }
    }
}
