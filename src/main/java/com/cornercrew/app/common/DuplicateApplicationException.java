package com.cornercrew.app.common;

public class DuplicateApplicationException extends RuntimeException {

    private final Long campaignId;
    private final Long controllerId;

    public DuplicateApplicationException(Long campaignId, Long controllerId) {
        super("Controller " + controllerId + " has already applied to campaign " + campaignId);
        this.campaignId = campaignId;
        this.controllerId = controllerId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getControllerId() {
        return controllerId;
    }
}
