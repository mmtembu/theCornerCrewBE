package com.cornercrew.app.traffic;

/**
 * Immutable record representing a geographic bounding box for traffic API queries.
 * This is a simpler version than the geolocation BoundingBox, without strict validation,
 * intended for use with traffic incident data retrieval.
 *
 * @param southLat southern boundary latitude
 * @param westLng western boundary longitude
 * @param northLat northern boundary latitude
 * @param eastLng eastern boundary longitude
 */
public record BoundingBox(
    double southLat,
    double westLng,
    double northLat,
    double eastLng
) {
    /**
     * Creates a bounding box centered on the given coordinates with the specified radius.
     * Uses the approximation that 1 degree of latitude ≈ 111 km.
     *
     * @param lat center latitude
     * @param lng center longitude
     * @param radiusKm radius in kilometers
     * @return a bounding box encompassing the area
     */
    public static BoundingBox fromCenter(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new BoundingBox(
            lat - latDelta,
            lng - lngDelta,
            lat + latDelta,
            lng + lngDelta
        );
    }
}
