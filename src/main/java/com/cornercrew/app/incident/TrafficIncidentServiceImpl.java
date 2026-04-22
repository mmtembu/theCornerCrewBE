package com.cornercrew.app.incident;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.IncidentProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.CongestionSnapshot;
import com.cornercrew.app.intersection.CongestionSnapshotRepository;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.cornercrew.app.traffic.BoundingBox;
import com.cornercrew.app.traffic.RawTrafficIncident;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TrafficIncidentServiceImpl implements TrafficIncidentService {

    private static final Logger log = LoggerFactory.getLogger(TrafficIncidentServiceImpl.class);

    private final TrafficApiAdapter trafficApiAdapter;
    private final IntersectionRepository intersectionRepository;
    private final CongestionSnapshotRepository congestionSnapshotRepository;
    private final IncidentProperties incidentProperties;
    private final TrafficMonitoringProperties trafficMonitoringProperties;

    public TrafficIncidentServiceImpl(TrafficApiAdapter trafficApiAdapter,
                                      IntersectionRepository intersectionRepository,
                                      CongestionSnapshotRepository congestionSnapshotRepository,
                                      IncidentProperties incidentProperties,
                                      TrafficMonitoringProperties trafficMonitoringProperties) {
        this.trafficApiAdapter = trafficApiAdapter;
        this.intersectionRepository = intersectionRepository;
        this.congestionSnapshotRepository = congestionSnapshotRepository;
        this.incidentProperties = incidentProperties;
        this.trafficMonitoringProperties = trafficMonitoringProperties;
    }

    @Override
    public List<TrafficIncidentDto> getIncidents(double latitude, double longitude, double radiusKm) {
        BoundingBox bbox = BoundingBox.fromCenter(latitude, longitude, radiusKm);

        try {
            List<RawTrafficIncident> rawIncidents = trafficApiAdapter.getIncidentData(bbox);
            List<Intersection> allIntersections = intersectionRepository.findAll();
            return enrichIncidents(rawIncidents, allIntersections);
        } catch (TrafficApiUnavailableException ex) {
            log.warn("Traffic API unavailable, falling back to CongestionSnapshot data: {}", ex.getMessage());
            return buildFallbackIncidents(bbox);
        }
    }

    private List<TrafficIncidentDto> enrichIncidents(List<RawTrafficIncident> rawIncidents,
                                                      List<Intersection> intersections) {
        double proximityMeters = incidentProperties.getLabelProximityMeters();
        List<TrafficIncidentDto> result = new ArrayList<>();

        for (RawTrafficIncident raw : rawIncidents) {
            String label = findNearestIntersectionLabel(raw.latitude(), raw.longitude(),
                    intersections, proximityMeters)
                    .orElse(raw.roadName());

            int delayMinutes = (int) Math.ceil(raw.delaySeconds() / 60.0);
            double speedKmh = clamp(raw.currentSpeedKmh(), 0, 200);
            delayMinutes = (int) clamp(delayMinutes, 0, 1440);

            result.add(new TrafficIncidentDto(
                    raw.id(),
                    raw.latitude(),
                    raw.longitude(),
                    label,
                    raw.startedAt().atOffset(ZoneOffset.UTC),
                    speedKmh,
                    delayMinutes,
                    formatDelay(delayMinutes)
            ));
        }

        return result;
    }

    private List<TrafficIncidentDto> buildFallbackIncidents(BoundingBox bbox) {
        double freeFlowSpeedKmh = trafficMonitoringProperties.getFreeFlowSpeedKmh();
        List<Intersection> allIntersections = intersectionRepository.findAll();
        List<TrafficIncidentDto> result = new ArrayList<>();

        for (Intersection intersection : allIntersections) {
            if (!isWithinBbox(intersection, bbox)) {
                continue;
            }

            Optional<CongestionSnapshot> optSnapshot =
                    congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(intersection.getId());
            if (optSnapshot.isEmpty()) {
                continue;
            }

            CongestionSnapshot snapshot = optSnapshot.get();
            double congestionScore = snapshot.getScore() != null ? snapshot.getScore() : 0.0;

            double speedKmh = clamp(freeFlowSpeedKmh * (1.0 - congestionScore), 0, 200);
            int delayMinutes = (int) Math.round(congestionScore * 30);
            delayMinutes = (int) clamp(delayMinutes, 0, 1440);

            result.add(new TrafficIncidentDto(
                    "snapshot-" + snapshot.getId(),
                    intersection.getLatitude(),
                    intersection.getLongitude(),
                    intersection.getLabel(),
                    snapshot.getMeasuredAt(),
                    speedKmh,
                    delayMinutes,
                    formatDelay(delayMinutes)
            ));
        }

        return result;
    }

    private Optional<String> findNearestIntersectionLabel(double lat, double lng,
                                                           List<Intersection> intersections,
                                                           double maxDistanceMeters) {
        String nearestLabel = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Intersection intersection : intersections) {
            if (intersection.getLatitude() == null || intersection.getLongitude() == null) {
                continue;
            }
            double distMeters = haversineMeters(lat, lng,
                    intersection.getLatitude(), intersection.getLongitude());
            if (distMeters <= maxDistanceMeters && distMeters < nearestDistance) {
                nearestDistance = distMeters;
                nearestLabel = intersection.getLabel();
            }
        }

        return Optional.ofNullable(nearestLabel);
    }

    private boolean isWithinBbox(Intersection intersection, BoundingBox bbox) {
        if (intersection.getLatitude() == null || intersection.getLongitude() == null) {
            return false;
        }
        double lat = intersection.getLatitude();
        double lng = intersection.getLongitude();
        return lat >= bbox.southLat() && lat <= bbox.northLat()
                && lng >= bbox.westLng() && lng <= bbox.eastLng();
    }

    static String formatDelay(int delayMinutes) {
        if (delayMinutes >= 60) {
            int hours = delayMinutes / 60;
            int minutes = delayMinutes % 60;
            return hours + "h " + minutes + "m";
        }
        return delayMinutes + " min";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
