package com.cornercrew.app.intersection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that polls traffic congestion data at configured intervals.
 * 
 * <p>This component invokes {@link TrafficMonitoringService#pollAndScore()} on a
 * fixed delay schedule. The polling interval is configurable via the
 * {@code app.traffic.polling-interval-ms} property (default: 300000ms = 5 minutes).
 * 
 * <p>The fixed delay ensures that the next poll cycle starts only after the
 * previous cycle completes, preventing overlapping executions.
 */
@Component
public class TrafficPollingJob {
    
    private static final Logger log = LoggerFactory.getLogger(TrafficPollingJob.class);
    
    private final TrafficMonitoringService trafficMonitoringService;
    
    public TrafficPollingJob(TrafficMonitoringService trafficMonitoringService) {
        this.trafficMonitoringService = trafficMonitoringService;
    }
    
    /**
     * Executes the traffic monitoring poll cycle at configured intervals.
     * 
     * <p>Uses fixed delay scheduling to ensure polls do not overlap. The delay
     * is measured from the completion of the previous execution to the start
     * of the next execution.
     * 
     * <p>Default interval: 300000ms (5 minutes)
     * Configure via: {@code app.traffic.polling-interval-ms}
     */
    @Scheduled(fixedDelayString = "${app.traffic.polling-interval-ms:300000}")
    public void pollTrafficData() {
        log.info("Traffic polling job triggered");
        try {
            trafficMonitoringService.pollAndScore();
        } catch (Exception e) {
            // Log error but don't propagate - allows scheduler to continue
            log.error("Traffic polling job failed: {}", e.getMessage(), e);
        }
    }
}
