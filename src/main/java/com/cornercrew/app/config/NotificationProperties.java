package com.cornercrew.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    private double commuteRadiusKm = 2.0;

    public double getCommuteRadiusKm() {
        return commuteRadiusKm;
    }

    public void setCommuteRadiusKm(double commuteRadiusKm) {
        this.commuteRadiusKm = commuteRadiusKm;
    }
}
