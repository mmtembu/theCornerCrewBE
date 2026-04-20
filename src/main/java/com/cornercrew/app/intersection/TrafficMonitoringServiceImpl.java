package com.cornercrew.app.intersection;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.geolocation.BoundingBox;
import com.cornercrew.app.geolocation.GeoLocationService;
import com.cornercrew.app.geolocation.IntersectionCoordinate;
import com.cornercrew.app.traffic.CongestionData;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link TrafficMonitoringService} that orchestrates
 * traffic monitoring workflow.
 * 
 * <p>This service:
 * <ul>
 *   <li>Resolves intersections via {@link GeoLocationService}</li>
 *   <li>Fetches congestion data via {@link TrafficApiAdapter}</li>
 *   <li>Persists {@link CongestionSnapshot} records</li>
 *   <li>Updates {@link Intersection} congestion scores and timestamps</li>
 *   <li>Flags high-congestion intersections via {@link IntersectionCandidateService}</li>
 *   <li>Handles API failures gracefully per-coordinate</li>
 * </ul>
 */
@Service
@Transactional
public class TrafficMonitoringServiceImpl implements TrafficMonitoringService {
    
    private static final Logger log = LoggerFactory.getLogger(TrafficMonitoringServiceImpl.class);
    
    private final GeoLocationService geoLocationService;
    private final TrafficApiAdapter trafficApiAdapter;
    private final IntersectionRepository intersectionRepository;
    private final CongestionSnapshotRepository congestionSnapshotRepository;
    private final IntersectionCandidateService intersectionCandidateService;
    private final TrafficMonitoringProperties properties;
    private final MeterRegistry meterRegistry;
    
    public TrafficMonitoringServiceImpl(
            GeoLocationService geoLocationService,
            TrafficApiAdapter trafficApiAdapter,
            IntersectionRepository intersectionRepository,
            CongestionSnapshotRepository congestionSnapshotRepository,
            IntersectionCandidateService intersectionCandidateService,
            TrafficMonitoringProperties properties,
            MeterRegistry meterRegistry) {
        this.geoLocationService = geoLocationService;
        this.trafficApiAdapter = trafficApiAdapter;
        this.intersectionRepository = intersectionRepository;
        this.congestionSnapshotRepository = congestionSnapshotRepository;
        this.intersectionCandidateService = intersectionCandidateService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public void pollAndScore() {
        log.info("Starting traffic monitoring poll cycle");
        
        // 1. Get bounding box from properties
        BoundingBox bbox = buildBoundingBox();
        
        // 2. Resolve intersections in the bounding box
        List<IntersectionCoordinate> coordinates = geoLocationService.resolveIntersections(bbox);
        log.info("Resolved {} intersections in bounding box", coordinates.size());
        
        // 3. Process each coordinate
        int successCount = 0;
        int errorCount = 0;
        
        for (IntersectionCoordinate coord : coordinates) {
            try {
                processCoordinate(coord);
                successCount++;
            } catch (TrafficApiUnavailableException e) {
                // Handle per-coordinate: log WARN, increment metric, skip coordinate, continue
                log.warn("Traffic API unavailable for coordinate {}: {}", coord.label(), e.getMessage());
                meterRegistry.counter("traffic.api.errors").increment();
                errorCount++;
            } catch (Exception e) {
                // Catch any other unexpected errors to prevent entire poll cycle from failing
                log.error("Unexpected error processing coordinate {}: {}", coord.label(), e.getMessage(), e);
                errorCount++;
            }
        }
        
        log.info("Traffic monitoring poll cycle completed: {} successful, {} errors", successCount, errorCount);
    }
    
    /**
     * Processes a single intersection coordinate: fetches congestion data,
     * persists snapshot, updates intersection, and flags if above threshold.
     */
    private void processCoordinate(IntersectionCoordinate coord) {
        // 4. Fetch congestion data via TrafficApiAdapter
        CongestionData congestionData = trafficApiAdapter.getCongestionData(coord.latitude(), coord.longitude());
        
        // 5. Clamp score to [0.0, 1.0] (defensive, as CongestionData already validates)
        double clampedScore = Math.max(0.0, Math.min(1.0, congestionData.score()));
        
        // 6. Find or create intersection
        Intersection intersection = intersectionRepository
                .findByLatitudeAndLongitude(coord.latitude(), coord.longitude())
                .orElseGet(() -> createNewIntersection(coord));
        
        // 7. Persist CongestionSnapshot
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setIntersectionId(intersection.getId());
        snapshot.setScore(clampedScore);
        snapshot.setRawLevel(congestionData.rawLevel());
        snapshot.setProvider(properties.getProvider());
        snapshot.setMeasuredAt(OffsetDateTime.ofInstant(congestionData.measuredAt(), ZoneOffset.UTC));
        snapshot.setRecordedAt(OffsetDateTime.now());
        congestionSnapshotRepository.save(snapshot);
        
        // 8. Update intersection congestionScore and lastCheckedAt
        intersection.setCongestionScore(clampedScore);
        intersection.setLastCheckedAt(OffsetDateTime.now());
        intersectionRepository.save(intersection);
        
        log.debug("Updated intersection {} with congestion score {}", intersection.getLabel(), clampedScore);
        
        // 9. Flag if score >= threshold
        if (clampedScore >= properties.getCongestionThreshold()) {
            intersectionCandidateService.flagIfNotAlready(intersection.getId());
            log.debug("Flagged intersection {} (score: {})", intersection.getLabel(), clampedScore);
        }
    }
    
    /**
     * Creates a new intersection entity from a coordinate.
     * New intersections start with status CANDIDATE.
     */
    private Intersection createNewIntersection(IntersectionCoordinate coord) {
        Intersection intersection = new Intersection();
        intersection.setLabel(coord.label());
        intersection.setLatitude(coord.latitude());
        intersection.setLongitude(coord.longitude());
        intersection.setType(IntersectionType.FOUR_WAY_STOP); // Default type
        intersection.setStatus(IntersectionStatus.CANDIDATE);
        return intersectionRepository.save(intersection);
    }
    
    /**
     * Builds a BoundingBox from the configured neighborhood properties.
     */
    private BoundingBox buildBoundingBox() {
        TrafficMonitoringProperties.Neighborhood neighborhood = properties.getNeighborhood();
        return new BoundingBox(
                neighborhood.getSouthLat(),
                neighborhood.getWestLng(),
                neighborhood.getNorthLat(),
                neighborhood.getEastLng()
        );
    }
    
    @Override
    public void pollAndScoreArea(BoundingBox bbox) {
        log.info("Starting on-demand traffic scan for area: {}", bbox);

        List<IntersectionCoordinate> coordinates = geoLocationService.resolveIntersections(bbox);
        log.info("Resolved {} intersections in user-specified area", coordinates.size());

        int successCount = 0;
        int errorCount = 0;

        for (IntersectionCoordinate coord : coordinates) {
            try {
                processCoordinate(coord);
                successCount++;
            } catch (TrafficApiUnavailableException e) {
                log.warn("Traffic API unavailable for coordinate {}: {}", coord.label(), e.getMessage());
                meterRegistry.counter("traffic.api.errors").increment();
                errorCount++;
            } catch (Exception e) {
                log.error("Unexpected error processing coordinate {}: {}", coord.label(), e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("On-demand traffic scan completed: {} successful, {} errors", successCount, errorCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Double> getLatestCongestionScore(Long intersectionId) {
        return congestionSnapshotRepository
                .findTopByIntersectionIdOrderByRecordedAtDesc(intersectionId)
                .map(CongestionSnapshot::getScore);
    }
}
