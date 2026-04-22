package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.CampaignStatus;

import java.util.List;

public interface CampaignMapService {

    List<CampaignMapDto> getCampaignsForMap(List<CampaignStatus> statuses,
                                             Double latitude,
                                             Double longitude,
                                             Double radiusKm);
}
