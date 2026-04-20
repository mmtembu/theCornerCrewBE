package com.cornercrew.app.campaign;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CampaignDto(
        Long id,
        String title,
        String description,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        CampaignStatus status,
        LocalDate windowStart,
        LocalDate windowEnd,
        OffsetDateTime lockedAt,
        OffsetDateTime createdAt
) {}
