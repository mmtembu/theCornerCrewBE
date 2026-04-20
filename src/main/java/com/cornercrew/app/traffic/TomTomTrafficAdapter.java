package com.cornercrew.app.traffic;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * TomTom Traffic Flow API adapter implementation.
 * 
 * <p>This adapter integrates with the TomTom Traffic Flow API to retrieve
 * real-time traffic congestion data. It normalizes TomTom's provider-specific
 * congestion levels to a 0.0-1.0 scale for consistent processing.
 * 
 * <p>Active when {@code app.traffic.provider=TOMTOM} is configured.
 * 
 * <p>API Documentation: https://developer.tomtom.com/traffic-api/documentation
 * 
 * <p>Error Handling:
 * <ul>
 *   <li>HTTP 429 (Too Many Requests): Logs warning and throws TrafficApiUnavailableException</li>
 *   <li>Connection errors: Throws TrafficApiUnavailableException</li>
 *   <li>API key is never logged for security</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.traffic.provider", havingValue = "TOMTOM")
public class TomTomTrafficAdapter implements TrafficApiAdapter {

    private static final Logger log = LoggerFactory.getLogger(TomTomTrafficAdapter.class);
    
    private static final String TOMTOM_TRAFFIC_FLOW_API_BASE = "https://api.tomtom.com/traffic/services/4/flowSegmentData";
    private static final String API_VERSION = "absolute/10/json";
    
    private final RestClient restClient;
    private final String apiKey;

    public TomTomTrafficAdapter(TrafficMonitoringProperties properties) {
        this.apiKey = properties.getApiKey();
        this.restClient = RestClient.builder()
                .baseUrl(TOMTOM_TRAFFIC_FLOW_API_BASE)
                .build();
        
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("TomTom API key is not configured. Traffic monitoring will fail.");
        }
    }

    @Override
    public CongestionData getCongestionData(double latitude, double longitude) {
        try {
            String endpoint = String.format("/%s?point=%f,%f&key=%s",
                    API_VERSION,
                    latitude,
                    longitude,
                    apiKey);
            
            log.debug("Fetching TomTom traffic data for coordinates: ({}, {})", latitude, longitude);
            
            TomTomResponse response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, resp) -> {
                        if (resp.getStatusCode().value() == 429) {
                            log.warn("TomTom API rate limit exceeded (HTTP 429). Consider increasing polling interval.");
                            throw new TrafficApiUnavailableException("TomTom API rate limit exceeded");
                        }
                        log.warn("TomTom API client error: HTTP {}", resp.getStatusCode().value());
                        throw new TrafficApiUnavailableException("TomTom API client error: " + resp.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, resp) -> {
                        log.warn("TomTom API server error: HTTP {}", resp.getStatusCode().value());
                        throw new TrafficApiUnavailableException("TomTom API server error: " + resp.getStatusCode());
                    })
                    .body(TomTomResponse.class);
            
            if (response == null || response.flowSegmentData() == null) {
                log.warn("TomTom API returned null or incomplete response for coordinates: ({}, {})", latitude, longitude);
                throw new TrafficApiUnavailableException("TomTom API returned incomplete response");
            }
            
            FlowSegmentData flowData = response.flowSegmentData();
            double normalizedScore = normalizeCurrentSpeed(flowData.currentSpeed(), flowData.freeFlowSpeed());
            String rawLevel = determineCongestionLevel(normalizedScore);
            
            log.debug("TomTom traffic data retrieved: score={}, rawLevel={} for ({}, {})", 
                    normalizedScore, rawLevel, latitude, longitude);
            
            return new CongestionData(normalizedScore, rawLevel, Instant.now());
            
        } catch (TrafficApiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch TomTom traffic data for coordinates: ({}, {}). Error: {}", 
                    latitude, longitude, e.getMessage());
            throw new TrafficApiUnavailableException("Failed to connect to TomTom API", e);
        }
    }

    /**
     * Normalizes TomTom's current speed vs free flow speed to a 0.0-1.0 congestion score.
     * 
     * <p>Calculation:
     * <ul>
     *   <li>score = 1.0 - (currentSpeed / freeFlowSpeed)</li>
     *   <li>0.0 = free flow (current speed equals free flow speed)</li>
     *   <li>1.0 = standstill (current speed is 0)</li>
     * </ul>
     * 
     * @param currentSpeed current traffic speed in km/h
     * @param freeFlowSpeed free flow speed in km/h
     * @return normalized congestion score in [0.0, 1.0]
     */
    private double normalizeCurrentSpeed(double currentSpeed, double freeFlowSpeed) {
        if (freeFlowSpeed <= 0) {
            log.warn("Invalid free flow speed: {}. Defaulting to moderate congestion.", freeFlowSpeed);
            return 0.5;
        }
        
        double ratio = currentSpeed / freeFlowSpeed;
        double congestionScore = 1.0 - ratio;
        
        // Clamp to [0.0, 1.0] range
        return Math.max(0.0, Math.min(1.0, congestionScore));
    }

    /**
     * Maps normalized congestion score to a human-readable level label.
     * 
     * @param score normalized congestion score in [0.0, 1.0]
     * @return congestion level label
     */
    private String determineCongestionLevel(double score) {
        if (score >= 0.8) return "HEAVY";
        if (score >= 0.5) return "MODERATE";
        if (score >= 0.2) return "LIGHT";
        return "FREE_FLOW";
    }

    /**
     * TomTom Traffic Flow API response structure.
     */
    private record TomTomResponse(FlowSegmentData flowSegmentData) {}

    /**
     * TomTom flow segment data containing speed information.
     */
    private record FlowSegmentData(
            double currentSpeed,
            double freeFlowSpeed,
            double currentTravelTime,
            double freeFlowTravelTime,
            double confidence
    ) {}
}
