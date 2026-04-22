package com.cornercrew.app.commuteprofile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SaveCommuteProfileRequest(
        @Min(-90) @Max(90) double originLatitude,
        @Min(-180) @Max(180) double originLongitude,
        @Min(-90) @Max(90) double destinationLatitude,
        @Min(-180) @Max(180) double destinationLongitude,
        @NotNull LocalTime departureStartTime,
        @NotNull LocalTime departureEndTime
) {}
