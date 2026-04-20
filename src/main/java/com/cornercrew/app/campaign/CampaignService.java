package com.cornercrew.app.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CampaignService {

    CampaignDto createCampaign(CreateCampaignRequest req, Long adminId);

    CampaignDto getCampaign(Long campaignId);

    Page<CampaignDto> listCampaigns(CampaignStatus status, Pageable pageable);

    void checkAndLockIfFunded(Long campaignId);

    CampaignDto autoProposeCampaign(Long intersectionId, Long adminId);

    CampaignDto approveCampaign(Long campaignId);
}
