package com.cornercrew.app.geolocation;

import com.cornercrew.app.common.GeoLocationApiUnavailableException;

import java.util.List;

/**
 * Provider-agnostic adapter interface for geocoding and intersection discovery.
 * 
 * <p>Concrete implementations can target different geocoding/mapping providers
 * (Google Maps Geocoding, Mapbox, HERE Geocoding, etc.) without changing
 * the core domain logic.
 * 
 * <p>The active implementation is selected via the {@code app.geolocation.provider}
 * configuration property.
 * 
 * <p>Known implementations:
 * <ul>
 *   <li>GoogleMapsGeoLocationAdapter - uses Google Maps Geocoding + Roads API</li>
 *   <li>MapboxGeoLocationAdapter - uses Mapbox Geocoding API</li>
 *   <li>HereGeoLocationAdapter - uses HERE Geocoding & Search API</li>
 * </ul>
 */
public interface GeoLocationApiAdapter {
    
    /**
     * Returns all intersection coordinates within the given bounding box.
     * 
     * @param bbox the geographic bounding box to search within
     * @return list of intersection coordinates found within the bounding box
     * @throws GeoLocationApiUnavailableException if the provider is unreachable or returns an error
     */
    List<IntersectionCoordinate> getIntersections(BoundingBox bbox);
    
    /**
     * Resolves a human-readable area name to a geographic bounding box.
     * 
     * @param areaName the area name to geocode (e.g., "Downtown Oakland")
     * @return the bounding box for the specified area
     * @throws GeoLocationApiUnavailableException if the provider is unreachable or returns an error
     */
    BoundingBox geocodeArea(String areaName);
}
