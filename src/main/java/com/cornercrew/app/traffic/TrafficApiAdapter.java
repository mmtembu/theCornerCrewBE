package com.cornercrew.app.traffic;

import com.cornercrew.app.common.TrafficApiUnavailableException;

/**
 * Provider-agnostic adapter interface for real-time traffic congestion data.
 * 
 * <p>Concrete implementations can target different traffic data providers
 * (Google Maps Traffic, TomTom Traffic, HERE Traffic, etc.) without changing
 * the core domain logic.
 * 
 * <p>The active implementation is selected via the {@code app.traffic.provider}
 * configuration property.
 * 
 * <p>Known implementations:
 * <ul>
 *   <li>GoogleMapsTrafficAdapter - uses Google Maps Roads / Distance Matrix API</li>
 *   <li>TomTomTrafficAdapter - uses TomTom Traffic Flow API</li>
 *   <li>HereTrafficAdapter - uses HERE Traffic Flow API</li>
 * </ul>
 */
public interface TrafficApiAdapter {
    
    /**
     * Returns the current congestion data for the given geographic coordinates.
     * 
     * @param latitude the latitude coordinate
     * @param longitude the longitude coordinate
     * @return congestion data with normalized score, provider-specific raw level, and measurement timestamp
     * @throws TrafficApiUnavailableException if the provider is unreachable or returns an error
     */
    CongestionData getCongestionData(double latitude, double longitude);
}
