package com.cornercrew.app.incident;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.IncidentProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.*;
import com.cornercrew.app.traffic.BoundingBox;
import com.cornercrew.app.traffic.RawTrafficIncident;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrafficIncidentServiceTest {

    @Mock
    private TrafficApiAdapter trafficApiAdapter;

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private CongestionSnapshotRepository congestionSnapshotRepository;

    private IncidentProperties incidentProperties;
    private TrafficMonitoringProperties trafficMonitoringProperties;

    private TrafficIncidentServiceImpl service;

    @BeforeEach
    void setUp() {
        incidentProperties = new IncidentProperties();
        incidentProperties.setMaxRadiusKm(50.0);
        incidentProperties.setLabelProximityMeters(200.0);

        trafficMonitoringProperties = new TrafficMonitoringProperties();
        trafficMonitoringProperties.setFreeFlowSpeedKmh(60.0);

        service = new TrafficIncidentServiceImpl(
                trafficApiAdapter,
                intersectionRepository,
                congestionSnapshotRepository,
                incidentProperties,
                trafficMonitoringProperties
        );
    }

    // --- Fallback behavior when TrafficApiAdapter throws TrafficApiUnavailableException ---

    @Test
    void getIncidents_apiUnavailable_returnsFallbackFromCongestionSnapshots() {
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class)))
                .thenThrow(new TrafficApiUnavailableException("API down"));

        // Intersection within the bounding box for center (37.8, -122.4) radius 5km
        Intersection intersection = buildIntersection(1L, "Oak Ave & Main St", 37.8, -122.4);
        when(intersectionRepository.findAll()).thenReturn(List.of(intersection));

        CongestionSnapshot snapshot = buildSnapshot(10L, 1L, 0.8, OffsetDateTime.now(ZoneOffset.UTC));
        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(1L))
                .thenReturn(Optional.of(snapshot));

        List<TrafficIncidentDto> result = service.getIncidents(37.8, -122.4, 5.0);

        assertEquals(1, result.size());
        TrafficIncidentDto dto = result.get(0);
        assertTrue(dto.id().startsWith("snapshot-"));
        assertEquals("snapshot-10", dto.id());
        assertEquals(37.8, dto.latitude());
        assertEquals(-122.4, dto.longitude());
        assertEquals("Oak Ave & Main St", dto.label());
    }

    // --- Default radius of 5 works correctly ---

    @Test
    void getIncidents_withRadius5_returnsApiResults() {
        RawTrafficIncident raw = new RawTrafficIncident(
                "inc-1", 37.8, -122.4, "Main St",
                Instant.now(), 30.0, 120
        );
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class)))
                .thenReturn(List.of(raw));
        when(intersectionRepository.findAll()).thenReturn(List.of());

        List<TrafficIncidentDto> result = service.getIncidents(37.8, -122.4, 5.0);

        assertEquals(1, result.size());
        assertEquals("inc-1", result.get(0).id());
    }

    // --- Empty result when API returns empty list ---

    @Test
    void getIncidents_apiReturnsEmptyList_returnsEmptyResult() {
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class)))
                .thenReturn(List.of());
        when(intersectionRepository.findAll()).thenReturn(List.of());

        List<TrafficIncidentDto> result = service.getIncidents(37.8, -122.4, 5.0);

        assertTrue(result.isEmpty());
    }

    // --- Fallback returns empty when no snapshots exist ---

    @Test
    void getIncidents_apiUnavailableAndNoSnapshots_returnsEmptyResult() {
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class)))
                .thenThrow(new TrafficApiUnavailableException("API down"));

        Intersection intersection = buildIntersection(1L, "Oak Ave & Main St", 37.8, -122.4);
        when(intersectionRepository.findAll()).thenReturn(List.of(intersection));
        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(1L))
                .thenReturn(Optional.empty());

        List<TrafficIncidentDto> result = service.getIncidents(37.8, -122.4, 5.0);

        assertTrue(result.isEmpty());
    }

    // --- Helper methods ---

    private Intersection buildIntersection(Long id, String label, double lat, double lng) {
        Intersection intersection = new Intersection();
        intersection.setId(id);
        intersection.setLabel(label);
        intersection.setLatitude(lat);
        intersection.setLongitude(lng);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CONFIRMED);
        return intersection;
    }

    private CongestionSnapshot buildSnapshot(Long id, Long intersectionId, double score,
                                              OffsetDateTime measuredAt) {
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setId(id);
        snapshot.setIntersectionId(intersectionId);
        snapshot.setScore(score);
        snapshot.setRawLevel("heavy");
        snapshot.setProvider("tomtom");
        snapshot.setMeasuredAt(measuredAt);
        snapshot.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return snapshot;
    }
}
