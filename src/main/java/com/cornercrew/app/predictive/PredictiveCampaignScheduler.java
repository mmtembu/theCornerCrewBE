package com.cornercrew.app.predictive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that runs predictive campaign drafting at configured intervals.
 *
 * <p>This component invokes {@link PredictiveCampaignService#detectAndDraftCampaigns()}
 * on a cron schedule. The schedule is configurable via the
 * {@code app.predictive.cron} property (default: "0 0 2 * * *" = daily at 02:00).
 */
@Component
public class PredictiveCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(PredictiveCampaignScheduler.class);

    private final PredictiveCampaignService predictiveCampaignService;

    public PredictiveCampaignScheduler(PredictiveCampaignService predictiveCampaignService) {
        this.predictiveCampaignService = predictiveCampaignService;
    }

    /**
     * Executes the predictive campaign drafting cycle on the configured cron schedule.
     *
     * <p>Default schedule: daily at 02:00 local time.
     * Configure via: {@code app.predictive.cron}
     */
    @Scheduled(cron = "${app.predictive.cron}")
    public void runPredictiveDrafting() {
        try {
            log.info("Starting predictive campaign drafting job");
            predictiveCampaignService.detectAndDraftCampaigns();
            log.info("Predictive campaign drafting job completed");
        } catch (Exception e) {
            log.error("Predictive campaign drafting job failed, will retry on next schedule", e);
        }
    }
}
