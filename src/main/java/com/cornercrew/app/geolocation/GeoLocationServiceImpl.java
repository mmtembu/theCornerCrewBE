package com.cornercrew.app.geolocation;

import com.cornercrew.app.common.GeoLocationApiUnavailableException;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.intersection.IntersectionStatus;
import com.cornercrew.app.intersection.IntersectionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link GeoLocationService} with caching and graceful degradation.
 * 
 * <p>This service resolves bounding boxes into intersection coordinates using
 * the configured {@link GeoLocationApiAdapter}, caches results for a configurable
 * TTL (default: 24 hours), and upserts Intersection records in the database.
 * 
 * <p>Error Handling:
 * <ul>
 *   <li>On {@link GeoLocationApiUnavailableException}: returns last cached result if available,
 *       otherwise aborts and logs WARN</li>
 *   <li>Increments {@code geolocation.api.errors} metric counter on adapter failure</li>
 *   <li>Validates returned coordinates fall within configured bounding box before upserting</li>
 * </ul>
 */
@Service
public class GeoLocationServiceImpl implements GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationServiceImpl.class);

    private final GeoLocationApiAdapter geoLocationApiAdapter;
    private final IntersectionRepository intersectionRepository;
    private final Counter apiErrorCounter;

    // Fallback cache for when API is unavailable
    private volatile List<IntersectionCoordinate> lastSuccessfulResult;
    private volatile BoundingBox lastSuccessfulBbox;

    public GeoLocationServiceImpl(
            GeoLocationApiAdapter geoLocationApiAdapter,
            IntersectionRepository intersectionRepository,
            MeterRegistry meterRegistry) {
        this.geoLocationApiAdapter = geoLocationApiAdapter;
        this.intersectionRepository = intersectionRepository;
        this.apiErrorCounter = Counter.builder("geolocation.api.errors")
                .description("Count of geolocation API failures")
                .register(meterRegistry);
    }

    @Override
    @Cacheable(value = "intersections", key = "#bbox.toString()")
    @Transactional
    public List<IntersectionCoordinate> resolveIntersections(BoundingBox bbox) {
        log.debug("Resolving intersections for bounding box: {}", bbox);

        try {
            // Call the adapter to get intersection coordinates
            List<IntersectionCoordinate> coordinates = geoLocationApiAdapter.getIntersections(bbox);

            // Validate and filter coordinates within bounding box
            List<IntersectionCoordinate> validCoordinates = new ArrayList<>();
            for (IntersectionCoordinate coord : coordinates) {
                if (isWithinBoundingBox(coord, bbox)) {
                    validCoordinates.add(coord);
                    upsertIntersection(coord);
                } else {
                    log.warn("Coordinate outside bounding box, discarding: {} (bbox: {})", coord, bbox);
                }
            }

            // Store successful result as fallback
            lastSuccessfulResult = validCoordinates;
            lastSuccessfulBbox = bbox;

            log.info("Successfully resolved {} intersections within bounding box", validCoordinates.size());
            return validCoordinates;

        } catch (GeoLocationApiUnavailableException e) {
            apiErrorCounter.increment();
            log.warn("GeoLocation API unavailable: {}. Attempting to use cached result.", e.getMessage());

            // Return last cached result if available and matches the requested bbox
            if (lastSuccessfulResult != null && bbox.equals(lastSuccessfulBbox)) {
                log.warn("Returning last successful result from fallback cache ({} intersections)", 
                        lastSuccessfulResult.size());
                return lastSuccessfulResult;
            }

            // No cached result available - abort
            log.warn("No cached result available for bounding box {}. Aborting poll cycle.", bbox);
            throw new GeoLocationApiUnavailableException(
                    "GeoLocation API unavailable and no cached result available", e);
        }
    }

    @Override
    public BoundingBox resolveAreaBoundingBox(String areaName) {
        log.debug("Resolving area bounding box for: {}", areaName);

        try {
            BoundingBox bbox = geoLocationApiAdapter.geocodeArea(areaName);
            log.info("Successfully resolved area '{}' to bounding box: {}", areaName, bbox);
            return bbox;

        } catch (GeoLocationApiUnavailableException e) {
            apiErrorCounter.increment();
            log.warn("GeoLocation API unavailable while resolving area '{}': {}", areaName, e.getMessage());
            throw e;
        }
    }

    /**
     * Validates that a coordinate falls within the given bounding box.
     * 
     * @param coord the coordinate to validate
     * @param bbox the bounding box
     * @return true if the coordinate is within the bounding box, false otherwise
     */
    private boolean isWithinBoundingBox(IntersectionCoordinate coord, BoundingBox bbox) {
        return coord.latitude() >= bbox.southLat() 
                && coord.latitude() <= bbox.northLat()
                && coord.longitude() >= bbox.westLng() 
                && coord.longitude() <= bbox.eastLng();
    }

    /**
     * Upserts an Intersection record in the database.
     * 
     * <p>If an intersection with the same coordinates already exists, updates its label.
     * Otherwise, creates a new intersection with status CANDIDATE.
     * 
     * @param coord the intersection coordinate to upsert
     */
    private void upsertIntersection(IntersectionCoordinate coord) {
        Optional<Intersection> existing = intersectionRepository
                .findByLatitudeAndLongitude(coord.latitude(), coord.longitude());

        if (existing.isPresent()) {
            // Update existing intersection label if changed
            Intersection intersection = existing.get();
            if (!coord.label().equals(intersection.getLabel())) {
                intersection.setLabel(coord.label());
                intersectionRepository.save(intersection);
                log.debug("Updated intersection label: {} -> {}", intersection.getId(), coord.label());
            }
        } else {
            // Create new intersection with status CANDIDATE
            Intersection intersection = new Intersection();
            intersection.setLabel(coord.label());
            intersection.setLatitude(coord.latitude());
            intersection.setLongitude(coord.longitude());
            intersection.setStatus(IntersectionStatus.CANDIDATE);
            intersection.setType(IntersectionType.FOUR_WAY_STOP); // Default type
            intersectionRepository.save(intersection);
            log.info("Created new intersection candidate: {} at ({}, {})", 
                    coord.label(), coord.latitude(), coord.longitude());
        }
    }
}
