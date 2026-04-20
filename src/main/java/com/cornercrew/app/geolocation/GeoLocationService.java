package com.cornercrew.app.geolocation;

import java.util.List;

/**
 * Service for resolving geographic areas into intersection coordinates
 * with caching support.
 * 
 * <p>This service acts as a facade over the {@link GeoLocationApiAdapter}
 * and provides caching to reduce external API calls. Results are cached
 * for a configurable TTL (default: 24 hours).
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Call {@link GeoLocationApiAdapter#getIntersections(BoundingBox)} and cache results</li>
 *   <li>Return cached results on cache hit without calling the adapter</li>
 *   <li>On cache miss, call adapter, cache result, and upsert Intersection records</li>
 *   <li>Handle {@link com.cornercrew.app.common.GeoLocationApiUnavailableException} gracefully</li>
 *   <li>Validate coordinates fall within configured bounding box before upserting</li>
 * </ul>
 */
public interface GeoLocationService {
    
    /**
     * Returns all known intersection coordinates within the given bounding box.
     * 
     * <p>Results are cached for a configurable TTL to reduce API call volume.
     * On cache hit, returns cached result without calling the adapter.
     * On cache miss, calls adapter, caches result, and upserts new Intersection
     * records with status CANDIDATE.
     * 
     * <p>If the {@link GeoLocationApiAdapter} throws a
     * {@link com.cornercrew.app.common.GeoLocationApiUnavailableException}:
     * <ul>
     *   <li>Returns last cached result if available</li>
     *   <li>Otherwise aborts and logs WARN</li>
     *   <li>Increments {@code geolocation.api.errors} metric counter</li>
     * </ul>
     * 
     * @param bbox the bounding box to search within
     * @return list of intersection coordinates within the bounding box
     */
    List<IntersectionCoordinate> resolveIntersections(BoundingBox bbox);
    
    /**
     * Resolves a human-readable area name (e.g., "Downtown Oakland") into a BoundingBox.
     * 
     * @param areaName the area name to geocode
     * @return the bounding box for the specified area
     */
    BoundingBox resolveAreaBoundingBox(String areaName);
}
