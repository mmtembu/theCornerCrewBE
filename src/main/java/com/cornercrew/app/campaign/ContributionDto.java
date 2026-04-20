package com.cornercrew.app.campaign;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ContributionDto(
        Long id,
        Long campaignId,
        Long driverId,
        BigDecimal amount,
        ContributionPeriod period,
        OffsetDateTime contributedAt
) {}
