package com.cornercrew.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payout")
public class PayoutProperties {

    private double ratingThreshold = 3.0;

    public double getRatingThreshold() {
        return ratingThreshold;
    }

    public void setRatingThreshold(double ratingThreshold) {
        this.ratingThreshold = ratingThreshold;
    }
}
