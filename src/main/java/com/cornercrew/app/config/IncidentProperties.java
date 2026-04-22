package com.cornercrew.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.incidents")
public class IncidentProperties {

    private double maxRadiusKm = 50.0;
    private double labelProximityMeters = 200.0;

    public double getMaxRadiusKm() {
        return maxRadiusKm;
    }

    public void setMaxRadiusKm(double maxRadiusKm) {
        this.maxRadiusKm = maxRadiusKm;
    }

    public double getLabelProximityMeters() {
        return labelProximityMeters;
    }

    public void setLabelProximityMeters(double labelProximityMeters) {
        this.labelProximityMeters = labelProximityMeters;
    }
}
