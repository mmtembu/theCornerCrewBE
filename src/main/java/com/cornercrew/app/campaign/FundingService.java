package com.cornercrew.app.campaign;

public interface FundingService {

    ContributionDto contribute(Long campaignId, Long driverId, ContributeRequest req);

    FundingSummaryDto getSummary(Long campaignId);
}
