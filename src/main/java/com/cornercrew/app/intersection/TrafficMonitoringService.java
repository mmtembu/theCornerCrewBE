package com.cornercrew.app.intersection;

import java.util.Optional;

/**
 * Service for monitoring traffic congestion at intersections.
 * 
 * <p>This service orchestrates the traffic monitoring workflow:
 * <ul>
 *   <li>Resolves intersections in the configured bounding box via {@link com.cornercrew.app.geolocation.GeoLocationService}</li>
 *   <li>Fetches congestion data for each intersection via {@link com.cornercrew.app.traffic.TrafficApiAdapter}</li>
 *   <li>Persists {@link CongestionSnapshot} records with provider, score, rawLevel, measuredAt, recordedAt</li>
 *   <li>Updates {@link Intersection#getCongestionScore()} and {@link Intersection#getLastCheckedAt()}</li>
 *   <li>Flags intersections when score >= threshold via {@link IntersectionCandidateService#flagIfNotAlready(Long)}</li>
 *   <li>Handles {@link com.cornercrew.app.common.TrafficApiUnavailableException} gracefully per-coordinate</li>
 * </ul>
 * 
 * <p>This service is invoked by a scheduled job at a configurable interval
 * (default: every 5 minutes via {@code app.traffic.polling-interval-ms}).
 */
public interface TrafficMonitoringService {
    
    /**
     * Entry point called by the @Scheduled job.
     * 
     * <p>Resolves intersections in the configured bounding box, fetches congestion
     * data for each, persists a CongestionSnapshot, updates intersection scores,
     * and flags those above threshold.
     * 
     * <p>Workflow:
     * <ol>
     *   <li>Get bounding box from {@link com.cornercrew.app.config.TrafficMonitoringProperties}</li>
     *   <li>Call {@link com.cornercrew.app.geolocation.GeoLocationService#resolveIntersections(com.cornercrew.app.geolocation.BoundingBox)}</li>
     *   <li>For each coordinate: fetch congestion via {@link com.cornercrew.app.traffic.TrafficApiAdapter#getCongestionData(double, double)}</li>
     *   <li>Handle {@link com.cornercrew.app.common.TrafficApiUnavailableException}: log WARN, increment metric, skip coordinate, continue</li>
     *   <li>Clamp score to [0.0, 1.0] before persisting</li>
     *   <li>Persist {@link CongestionSnapshot} with all fields</li>
     *   <li>Update {@link Intersection#getCongestionScore()} and {@link Intersection#getLastCheckedAt()}</li>
     *   <li>If score >= threshold: call {@link IntersectionCandidateService#flagIfNotAlready(Long)}</li>
     * </ol>
     * 
     * <p>Per-coordinate error handling ensures that a failure on one coordinate
     * does not prevent processing of other coordinates in the same poll cycle.
     */
    void pollAndScore();

    /**
     * Polls and scores intersections within the given bounding box.
     * Same workflow as pollAndScore() but for a user-specified area.
     */
    void pollAndScoreArea(com.cornercrew.app.geolocation.BoundingBox bbox);

    /**
     * Returns the latest congestion score for a given intersection.
     * 
     * <p>Queries the most recent {@link CongestionSnapshot} for the given
     * intersectionId ordered by recordedAt descending.
     * 
     * @param intersectionId the intersection ID
     * @return the latest congestion score, or empty if no snapshots exist
     */
    Optional<Double> getLatestCongestionScore(Long intersectionId);
}
