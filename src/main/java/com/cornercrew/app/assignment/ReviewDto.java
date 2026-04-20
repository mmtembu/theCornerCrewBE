package com.cornercrew.app.assignment;

import java.time.OffsetDateTime;

public record ReviewDto(
        Long id,
        Long assignmentId,
        Long driverId,
        int rating,
        String comment,
        OffsetDateTime reviewedAt
) {}
