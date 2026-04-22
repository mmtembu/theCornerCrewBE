package com.cornercrew.app.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.predictive")
public class PredictiveProperties {

    private int lookbackWeeks = 4;
    private int minOccurrences = 3;
    private BigDecimal defaultTargetAmount = new BigDecimal("5000.00");
    private String cron = "0 0 2 * * *";

    public int getLookbackWeeks() {
        return lookbackWeeks;
    }

    public void setLookbackWeeks(int lookbackWeeks) {
        this.lookbackWeeks = lookbackWeeks;
    }

    public int getMinOccurrences() {
        return minOccurrences;
    }

    public void setMinOccurrences(int minOccurrences) {
        this.minOccurrences = minOccurrences;
    }

    public BigDecimal getDefaultTargetAmount() {
        return defaultTargetAmount;
    }

    public void setDefaultTargetAmount(BigDecimal defaultTargetAmount) {
        this.defaultTargetAmount = defaultTargetAmount;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
