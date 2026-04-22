package com.cornercrew.app;

import com.cornercrew.app.config.GeoLocationProperties;
import com.cornercrew.app.config.IncidentProperties;
import com.cornercrew.app.config.NotificationProperties;
import com.cornercrew.app.config.PayoutProperties;
import com.cornercrew.app.config.PredictiveProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties({
    TrafficMonitoringProperties.class,
    GeoLocationProperties.class,
    PayoutProperties.class,
    NotificationProperties.class,
    PredictiveProperties.class,
    IncidentProperties.class
})
public class CornerCrewApplication {
    public static void main(String[] args) {
        SpringApplication.run(CornerCrewApplication.class, args);
    }
}
