package com.cornercrew.app.traffic;

import java.time.Instant;

/**
 * Immutable record representing congestion data from a traffic API provider.
 * 
 * @param score normalized congestion score in [0.0, 1.0] where 0.0 = free flow, 1.0 = standstill
 * @param rawLevel provider-specific congestion level label (e.g., "HEAVY", "MODERATE", "LIGHT")
 * @param measuredAt timestamp when the provider measured the traffic data
 */
public record CongestionData(
    double score,
    String rawLevel,
    Instant measuredAt
) {
    public CongestionData {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Congestion score must be in range [0.0, 1.0], got: " + score);
        }
        if (rawLevel == null || rawLevel.isBlank()) {
            throw new IllegalArgumentException("Raw level cannot be null or blank");
        }
        if (measuredAt == null) {
            throw new IllegalArgumentException("Measured at timestamp cannot be null");
        }
    }
}
