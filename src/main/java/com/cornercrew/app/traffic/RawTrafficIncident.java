package com.cornercrew.app.traffic;

import java.time.Instant;

/**
 * Immutable record representing raw traffic incident data from a traffic API provider.
 *
 * @param id unique incident identifier from the provider
 * @param latitude latitude coordinate of the incident
 * @param longitude longitude coordinate of the incident
 * @param roadName name of the road where the incident occurred
 * @param startedAt timestamp when the incident started
 * @param currentSpeedKmh current traffic speed at the incident location in km/h
 * @param delaySeconds estimated delay caused by the incident in seconds
 */
public record RawTrafficIncident(
    String id,
    double latitude,
    double longitude,
    String roadName,
    Instant startedAt,
    double currentSpeedKmh,
    int delaySeconds
) {}
