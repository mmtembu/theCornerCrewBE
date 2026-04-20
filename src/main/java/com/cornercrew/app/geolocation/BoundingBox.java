package com.cornercrew.app.geolocation;

/**
 * Immutable record representing a geographic bounding box defined by
 * south/north latitudes and west/east longitudes.
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
    public BoundingBox {
        if (southLat < -90.0 || southLat > 90.0) {
            throw new IllegalArgumentException("South latitude must be in range [-90, 90], got: " + southLat);
        }
        if (northLat < -90.0 || northLat > 90.0) {
            throw new IllegalArgumentException("North latitude must be in range [-90, 90], got: " + northLat);
        }
        if (westLng < -180.0 || westLng > 180.0) {
            throw new IllegalArgumentException("West longitude must be in range [-180, 180], got: " + westLng);
        }
        if (eastLng < -180.0 || eastLng > 180.0) {
            throw new IllegalArgumentException("East longitude must be in range [-180, 180], got: " + eastLng);
        }
        if (southLat >= northLat) {
            throw new IllegalArgumentException("South latitude must be less than north latitude, got south=" + southLat + ", north=" + northLat);
        }
        if (westLng >= eastLng) {
            throw new IllegalArgumentException("West longitude must be less than east longitude, got west=" + westLng + ", east=" + eastLng);
        }
    }
}
