package com.cornercrew.app.commuteprofile;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record CommuteProfileDto(
        Long id,
        Long driverId,
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude,
        LocalTime departureStartTime,
        LocalTime departureEndTime,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
