package com.cornercrew.app.geolocation;

/**
 * Immutable record representing the geographic coordinates and label of an intersection.
 * 
 * @param latitude the latitude coordinate
 * @param longitude the longitude coordinate
 * @param label human-readable intersection label (e.g., "Oak Ave & Main St")
 */
public record IntersectionCoordinate(
    double latitude,
    double longitude,
    String label
) {
    public IntersectionCoordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be in range [-90, 90], got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be in range [-180, 180], got: " + longitude);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label cannot be null or blank");
        }
    }
}
