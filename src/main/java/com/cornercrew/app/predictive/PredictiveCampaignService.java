package com.cornercrew.app.predictive;

import java.util.List;

public interface PredictiveCampaignService {

    /**
     * Detects recurrence patterns and drafts campaigns for each pattern.
     * This is the main entry point called by the scheduled job.
     */
    void detectAndDraftCampaigns();

    /**
     * Analyzes historical CongestionSnapshot data to detect recurring
     * congestion patterns grouped by intersection, day-of-week, and
     * 2-hour time bucket.
     *
     * @return list of detected recurrence patterns
     */
    List<RecurrencePatternDto> detectPatterns();
}
