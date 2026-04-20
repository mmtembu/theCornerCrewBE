package com.cornercrew.app.intersection;

import com.cornercrew.app.geolocation.BoundingBox;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for location-based traffic scanning.
 *
 * <p>Any authenticated user can submit their current coordinates to trigger
 * an on-demand traffic scan of the surrounding area and retrieve nearby
 * intersections with their congestion data.
 */
@RestController
@RequestMapping("/intersections/nearby")
public class NearbyTrafficController {

    private static final double DEFAULT_RADIUS_DEGREES = 0.01; // ~1.1 km

    private final TrafficMonitoringService trafficMonitoringService;
    private final IntersectionRepository intersectionRepository;

    public NearbyTrafficController(TrafficMonitoringService trafficMonitoringService,
                                   IntersectionRepository intersectionRepository) {
        this.trafficMonitoringService = trafficMonitoringService;
        this.intersectionRepository = intersectionRepository;
    }

    /**
     * Scans the area around the user's current location for traffic congestion.
     *
     * <p>Creates a bounding box around the given coordinates, discovers
     * intersections, fetches live congestion data, and returns the results.
     *
     * @param request the user's latitude and longitude
     * @return list of nearby intersections with congestion scores
     */
    @PostMapping("/scan")
    public ResponseEntity<List<NearbyIntersectionDto>> scanNearby(@Valid @RequestBody ScanRequest request) {
        double radius = request.radiusDegrees() != null ? request.radiusDegrees() : DEFAULT_RADIUS_DEGREES;

        BoundingBox bbox = new BoundingBox(
                request.latitude() - radius,
                request.longitude() - radius,
                request.latitude() + radius,
                request.longitude() + radius
        );

        // Run on-demand traffic scan
        trafficMonitoringService.pollAndScoreArea(bbox);

        // Return all intersections within the scanned area
        List<Intersection> nearby = intersectionRepository.findAll().stream()
                .filter(i -> i.getLatitude() != null && i.getLongitude() != null)
                .filter(i -> i.getLatitude() >= bbox.southLat() && i.getLatitude() <= bbox.northLat())
                .filter(i -> i.getLongitude() >= bbox.westLng() && i.getLongitude() <= bbox.eastLng())
                .toList();

        List<NearbyIntersectionDto> results = nearby.stream()
                .map(i -> new NearbyIntersectionDto(
                        i.getId(),
                        i.getLabel(),
                        i.getLatitude(),
                        i.getLongitude(),
                        i.getStatus(),
                        i.getCongestionScore(),
                        i.getLastCheckedAt()
                ))
                .toList();

        return ResponseEntity.ok(results);
    }

    public record ScanRequest(
            @Min(-90) @Max(90) double latitude,
            @Min(-180) @Max(180) double longitude,
            @Positive Double radiusDegrees
    ) {}

    public record NearbyIntersectionDto(
            Long id,
            String label,
            Double latitude,
            Double longitude,
            IntersectionStatus status,
            Double congestionScore,
            java.time.OffsetDateTime lastCheckedAt
    ) {}
}
