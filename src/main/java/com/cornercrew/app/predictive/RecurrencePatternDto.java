package com.cornercrew.app.predictive;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record RecurrencePatternDto(
    Long id,
    Long intersectionId,
    String intersectionLabel,
    DayOfWeek dayOfWeek,
    LocalTime timeBucketStart,
    LocalTime timeBucketEnd,
    int occurrenceCount,
    double averageCongestionScore,
    OffsetDateTime detectedAt
) {}
