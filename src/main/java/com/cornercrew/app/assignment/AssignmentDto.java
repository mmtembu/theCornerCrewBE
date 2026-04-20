package com.cornercrew.app.assignment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AssignmentDto(
        Long id,
        Long campaignId,
        Long controllerId,
        Long intersectionId,
        AssignmentStatus status,
        BigDecimal agreedPay,
        OffsetDateTime assignedAt,
        OffsetDateTime paidAt
) {}
