package com.cornercrew.app.assignment;

import java.time.OffsetDateTime;

public record ApplicationDto(
        Long id,
        Long campaignId,
        Long controllerId,
        ApplicationStatus status,
        String note,
        OffsetDateTime appliedAt
) {}
