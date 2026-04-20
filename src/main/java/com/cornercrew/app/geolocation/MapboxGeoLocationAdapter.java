package com.cornercrew.app.geolocation;

import com.cornercrew.app.common.GeoLocationApiUnavailableException;
import com.cornercrew.app.config.GeoLocationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapbox Geocoding API adapter implementation.
 *
 * <p>Uses Mapbox reverse geocoding to discover named intersections within a
 * bounding box. The adapter samples a grid of points across the bounding box
 * and reverse-geocodes each point to find nearby street addresses, which serve
 * as intersection proxies for traffic monitoring.
 *
 * <p>Active when {@code app.geolocation.provider=MAPBOX} is configured.
 */
@Component
@ConditionalOnProperty(name = "app.geolocation.provider", havingValue = "MAPBOX")
public class MapboxGeoLocationAdapter implements GeoLocationApiAdapter {

    private static final Logger log = LoggerFactory.getLogger(MapboxGeoLocationAdapter.class);

    private static final String MAPBOX_GEOCODING_API_BASE = "https://api.mapbox.com/search/geocode/v6";
    private static final int GRID_SIZE = 3; // 3x3 grid = 9 sample points

    private final RestClient restClient;
    private final String apiKey;

    public MapboxGeoLocationAdapter(GeoLocationProperties properties) {
        this.apiKey = properties.getApiKey();
        this.restClient = RestClient.builder()
                .baseUrl(MAPBOX_GEOCODING_API_BASE)
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Mapbox API key is not configured. Geolocation services will fail.");
        }
    }

    @Override
    public List<IntersectionCoordinate> getIntersections(BoundingBox bbox) {
        try {
            List<IntersectionCoordinate> intersections = new ArrayList<>();

            double latStep = (bbox.northLat() - bbox.southLat()) / (GRID_SIZE + 1);
            double lngStep = (bbox.eastLng() - bbox.westLng()) / (GRID_SIZE + 1);

            for (int row = 1; row <= GRID_SIZE; row++) {
                for (int col = 1; col <= GRID_SIZE; col++) {
                    double lat = bbox.southLat() + row * latStep;
                    double lng = bbox.westLng() + col * lngStep;

                    try {
                        IntersectionCoordinate coord = reverseGeocode(lat, lng, bbox);
                        if (coord != null) {
                            intersections.add(coord);
                        }
                    } catch (Exception e) {
                        log.debug("Reverse geocode failed for ({}, {}): {}", lat, lng, e.getMessage());
                    }
                }
            }

            log.info("Discovered {} intersections within bounding box via reverse geocoding", intersections.size());
            return intersections;

        } catch (Exception e) {
            log.warn("Failed to discover intersections via Mapbox: {}", e.getMessage());
            throw new GeoLocationApiUnavailableException("Failed to connect to Mapbox API", e);
        }
    }

    /**
     * Reverse-geocodes a single point to find the nearest street address.
     */
    private IntersectionCoordinate reverseGeocode(double lat, double lng, BoundingBox bbox) {
        String endpoint = String.format(
                "/reverse?longitude=%f&latitude=%f&types=address,street&limit=1&access_token=%s",
                lng, lat, apiKey);

        MapboxGeocodingResponse response = restClient.get()
                .uri(endpoint)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                    if (resp.getStatusCode().value() == 429) {
                        log.warn("Mapbox API rate limit exceeded (HTTP 429)");
                        throw new GeoLocationApiUnavailableException("Mapbox API rate limit exceeded");
                    }
                    throw new GeoLocationApiUnavailableException("Mapbox API error: " + resp.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                    throw new GeoLocationApiUnavailableException("Mapbox API server error: " + resp.getStatusCode());
                })
                .body(MapboxGeocodingResponse.class);

        if (response == null || response.features() == null || response.features().isEmpty()) {
            return null;
        }

        MapboxFeature feature = response.features().get(0);
        if (feature.geometry() == null || feature.geometry().coordinates() == null
                || feature.geometry().coordinates().length < 2) {
            return null;
        }

        double longitude = feature.geometry().coordinates()[0];
        double latitude = feature.geometry().coordinates()[1];

        // Validate within bounding box
        if (latitude < bbox.southLat() || latitude > bbox.northLat()
                || longitude < bbox.westLng() || longitude > bbox.eastLng()) {
            return null;
        }

        String label = extractLabel(feature);
        return new IntersectionCoordinate(latitude, longitude, label);
    }

    @Override
    public BoundingBox geocodeArea(String areaName) {
        try {
            String endpoint = String.format(
                    "/forward?q=%s&types=place,locality,neighborhood&limit=1&access_token=%s",
                    areaName.replace(" ", "%20"),
                    apiKey);

            MapboxGeocodingResponse response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        throw new GeoLocationApiUnavailableException("Mapbox API error: " + resp.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                        throw new GeoLocationApiUnavailableException("Mapbox API server error: " + resp.getStatusCode());
                    })
                    .body(MapboxGeocodingResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                throw new GeoLocationApiUnavailableException("No results for area: " + areaName);
            }

            MapboxFeature feature = response.features().get(0);

            if (feature.properties() != null && feature.properties().bbox() != null) {
                double[] box = feature.properties().bbox();
                if (box.length >= 4) {
                    return new BoundingBox(box[1], box[0], box[3], box[2]);
                }
            }

            // Fallback: ~1km box around center
            if (feature.geometry() != null && feature.geometry().coordinates() != null) {
                double[] coords = feature.geometry().coordinates();
                if (coords.length >= 2) {
                    double delta = 0.01;
                    return new BoundingBox(
                            coords[1] - delta, coords[0] - delta,
                            coords[1] + delta, coords[0] + delta);
                }
            }

            throw new GeoLocationApiUnavailableException("Invalid feature data for area: " + areaName);

        } catch (GeoLocationApiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new GeoLocationApiUnavailableException("Failed to geocode area: " + areaName, e);
        }
    }

    private String extractLabel(MapboxFeature feature) {
        if (feature.properties() != null) {
            if (feature.properties().name() != null && !feature.properties().name().isBlank()) {
                return feature.properties().name();
            }
            if (feature.properties().full_address() != null && !feature.properties().full_address().isBlank()) {
                return feature.properties().full_address();
            }
        }
        if (feature.geometry() != null && feature.geometry().coordinates() != null) {
            double[] coords = feature.geometry().coordinates();
            if (coords.length >= 2) {
                return String.format("%.4f, %.4f", coords[1], coords[0]);
            }
        }
        return "Unknown Location";
    }

    private record MapboxGeocodingResponse(String type, List<MapboxFeature> features) {}
    private record MapboxFeature(String type, MapboxGeometry geometry, MapboxProperties properties) {}
    private record MapboxGeometry(String type, double[] coordinates) {}
    private record MapboxProperties(String name, String full_address, String place_name,
                                    double[] bbox, MapboxContext context) {}
    private record MapboxContext(String country, String region, String place,
                                 String locality, String neighborhood) {}
}
