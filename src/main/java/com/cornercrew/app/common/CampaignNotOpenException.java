package com.cornercrew.app.common;

public class CampaignNotOpenException extends RuntimeException {

    private final Long campaignId;

    public CampaignNotOpenException(Long campaignId) {
        super("Campaign " + campaignId + " is not open for contributions");
        this.campaignId = campaignId;
    }

    public Long getCampaignId() {
        return campaignId;
    }
}
