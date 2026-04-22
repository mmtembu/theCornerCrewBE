package com.cornercrew.app.incident;

import java.time.OffsetDateTime;

public record TrafficIncidentDto(
    String id,
    double latitude,
    double longitude,
    String label,
    OffsetDateTime startedAt,
    double averageSpeedKmh,
    int estimatedDelayMinutes,
    String estimatedDelayFormatted
) {}
