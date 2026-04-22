package com.cornercrew.app.campaignmap;

import com.cornercrew.app.campaign.CampaignStatus;

import java.math.BigDecimal;
import java.util.List;

public record CampaignMapDto(
        Long campaignId,
        String title,
        CampaignStatus status,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        double fundingPercentage,
        List<IntersectionMapDto> intersections
) {}
