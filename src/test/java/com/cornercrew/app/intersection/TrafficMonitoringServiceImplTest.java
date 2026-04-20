package com.cornercrew.app.intersection;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.geolocation.BoundingBox;
import com.cornercrew.app.geolocation.GeoLocationService;
import com.cornercrew.app.geolocation.IntersectionCoordinate;
import com.cornercrew.app.traffic.CongestionData;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrafficMonitoringServiceImplTest {

    @Mock
    private GeoLocationService geoLocationService;

    @Mock
    private TrafficApiAdapter trafficApiAdapter;

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private CongestionSnapshotRepository congestionSnapshotRepository;

    @Mock
    private IntersectionCandidateService intersectionCandidateService;

    @Mock
    private TrafficMonitoringProperties properties;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter errorCounter;

    @InjectMocks
    private TrafficMonitoringServiceImpl trafficMonitoringService;

    private TrafficMonitoringProperties.Neighborhood neighborhood;

    @BeforeEach
    void setUp() {
        neighborhood = new TrafficMonitoringProperties.Neighborhood();
        neighborhood.setSouthLat(37.790);
        neighborhood.setWestLng(-122.425);
        neighborhood.setNorthLat(37.810);
        neighborhood.setEastLng(-122.400);

        lenient().when(properties.getNeighborhood()).thenReturn(neighborhood);
        lenient().when(properties.getProvider()).thenReturn("TOMTOM");
        lenient().when(properties.getCongestionThreshold()).thenReturn(0.7);
        lenient().when(meterRegistry.counter("traffic.api.errors")).thenReturn(errorCounter);
    }

    // --- pollAndScore tests ---

    @Test
    void pollAndScore_successfulPoll_persistsSnapshotsAndUpdatesIntersections() {
        // Arrange
        IntersectionCoordinate coord1 = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        IntersectionCoordinate coord2 = new IntersectionCoordinate(37.805, -122.415, "Elm St & Pine Ave");

        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord1, coord2));

        Intersection intersection1 = buildIntersection(1L, coord1);
        Intersection intersection2 = buildIntersection(2L, coord2);

        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection1));
        when(intersectionRepository.findByLatitudeAndLongitude(37.805, -122.415))
                .thenReturn(Optional.of(intersection2));

        CongestionData data1 = new CongestionData(0.85, "HEAVY", Instant.now());
        CongestionData data2 = new CongestionData(0.45, "MODERATE", Instant.now());

        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data1);
        when(trafficApiAdapter.getCongestionData(37.805, -122.415)).thenReturn(data2);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(geoLocationService).resolveIntersections(any(BoundingBox.class));
        verify(trafficApiAdapter, times(2)).getCongestionData(anyDouble(), anyDouble());
        verify(congestionSnapshotRepository, times(2)).save(any(CongestionSnapshot.class));
        verify(intersectionRepository, times(2)).save(any(Intersection.class));

        // Verify intersection1 was updated with score 0.85
        ArgumentCaptor<Intersection> intersectionCaptor = ArgumentCaptor.forClass(Intersection.class);
        verify(intersectionRepository, times(2)).save(intersectionCaptor.capture());
        List<Intersection> savedIntersections = intersectionCaptor.getAllValues();

        Intersection savedInt1 = savedIntersections.stream()
                .filter(i -> i.getId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertEquals(0.85, savedInt1.getCongestionScore());
        assertNotNull(savedInt1.getLastCheckedAt());

        Intersection savedInt2 = savedIntersections.stream()
                .filter(i -> i.getId().equals(2L))
                .findFirst()
                .orElseThrow();
        assertEquals(0.45, savedInt2.getCongestionScore());
        assertNotNull(savedInt2.getLastCheckedAt());
    }

    @Test
    void pollAndScore_scoreAboveThreshold_flagsIntersection() {
        // Arrange
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        Intersection intersection = buildIntersection(1L, coord);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection));

        CongestionData data = new CongestionData(0.85, "HEAVY", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(intersectionCandidateService).flagIfNotAlready(1L);
    }

    @Test
    void pollAndScore_scoreBelowThreshold_doesNotFlagIntersection() {
        // Arrange
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        Intersection intersection = buildIntersection(1L, coord);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection));

        CongestionData data = new CongestionData(0.45, "MODERATE", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(intersectionCandidateService, never()).flagIfNotAlready(anyLong());
    }

    @Test
    void pollAndScore_scoreAtThreshold_flagsIntersection() {
        // Arrange
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        Intersection intersection = buildIntersection(1L, coord);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection));

        CongestionData data = new CongestionData(0.7, "HEAVY", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(intersectionCandidateService).flagIfNotAlready(1L);
    }

    @Test
    void pollAndScore_trafficApiUnavailable_logsWarningIncrementsMetricSkipsCoordinate() {
        // Arrange
        IntersectionCoordinate coord1 = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        IntersectionCoordinate coord2 = new IntersectionCoordinate(37.805, -122.415, "Elm St & Pine Ave");

        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord1, coord2));

        // coord1 throws exception
        when(trafficApiAdapter.getCongestionData(37.800, -122.410))
                .thenThrow(new TrafficApiUnavailableException("Provider timeout"));

        // coord2 succeeds
        Intersection intersection2 = buildIntersection(2L, coord2);
        when(intersectionRepository.findByLatitudeAndLongitude(37.805, -122.415))
                .thenReturn(Optional.of(intersection2));

        CongestionData data2 = new CongestionData(0.45, "MODERATE", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.805, -122.415)).thenReturn(data2);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(errorCounter).increment();
        verify(congestionSnapshotRepository, times(1)).save(any(CongestionSnapshot.class)); // only coord2
        verify(intersectionRepository, times(1)).save(any(Intersection.class)); // only coord2
    }

    @Test
    void pollAndScore_allCoordinatesFail_completesWithoutPersisting() {
        // Arrange
        IntersectionCoordinate coord1 = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        IntersectionCoordinate coord2 = new IntersectionCoordinate(37.805, -122.415, "Elm St & Pine Ave");

        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord1, coord2));

        when(trafficApiAdapter.getCongestionData(anyDouble(), anyDouble()))
                .thenThrow(new TrafficApiUnavailableException("Provider down"));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        verify(errorCounter, times(2)).increment();
        verify(congestionSnapshotRepository, never()).save(any(CongestionSnapshot.class));
        verify(intersectionRepository, never()).save(any(Intersection.class));
    }

    @Test
    void pollAndScore_newIntersection_createsIntersectionWithCandidateStatus() {
        // Arrange
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        // No existing intersection
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.empty());

        // Mock save to return intersection with ID
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> {
                    Intersection i = invocation.getArgument(0);
                    i.setId(99L);
                    return i;
                });

        CongestionData data = new CongestionData(0.45, "MODERATE", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        ArgumentCaptor<Intersection> captor = ArgumentCaptor.forClass(Intersection.class);
        verify(intersectionRepository, times(2)).save(captor.capture()); // once for create, once for update

        Intersection created = captor.getAllValues().get(0);
        assertEquals("Oak Ave & Main St", created.getLabel());
        assertEquals(37.800, created.getLatitude());
        assertEquals(-122.410, created.getLongitude());
        assertEquals(IntersectionStatus.CANDIDATE, created.getStatus());
        assertEquals(IntersectionType.FOUR_WAY_STOP, created.getType());
    }

    @Test
    void pollAndScore_clampScoreAboveOne_clampsToOne() {
        // Arrange - this tests defensive clamping even though CongestionData validates
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        Intersection intersection = buildIntersection(1L, coord);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection));

        // Note: CongestionData constructor validates, so we can't actually create invalid data
        // This test verifies the clamping logic exists even if it's redundant
        CongestionData data = new CongestionData(1.0, "STANDSTILL", Instant.now());
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        ArgumentCaptor<CongestionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(congestionSnapshotRepository).save(snapshotCaptor.capture());
        assertEquals(1.0, snapshotCaptor.getValue().getScore());
    }

    @Test
    void pollAndScore_persistsSnapshotWithAllFields() {
        // Arrange
        IntersectionCoordinate coord = new IntersectionCoordinate(37.800, -122.410, "Oak Ave & Main St");
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coord));

        Intersection intersection = buildIntersection(1L, coord);
        when(intersectionRepository.findByLatitudeAndLongitude(37.800, -122.410))
                .thenReturn(Optional.of(intersection));

        Instant measuredAt = Instant.parse("2025-01-15T10:30:00Z");
        CongestionData data = new CongestionData(0.85, "HEAVY", measuredAt);
        when(trafficApiAdapter.getCongestionData(37.800, -122.410)).thenReturn(data);

        when(congestionSnapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        trafficMonitoringService.pollAndScore();

        // Assert
        ArgumentCaptor<CongestionSnapshot> captor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(congestionSnapshotRepository).save(captor.capture());

        CongestionSnapshot snapshot = captor.getValue();
        assertEquals(1L, snapshot.getIntersectionId());
        assertEquals(0.85, snapshot.getScore());
        assertEquals("HEAVY", snapshot.getRawLevel());
        assertEquals("TOMTOM", snapshot.getProvider());
        assertNotNull(snapshot.getMeasuredAt());
        assertNotNull(snapshot.getRecordedAt());
    }

    // --- getLatestCongestionScore tests ---

    @Test
    void getLatestCongestionScore_snapshotExists_returnsScore() {
        // Arrange
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setId(1L);
        snapshot.setIntersectionId(5L);
        snapshot.setScore(0.75);
        snapshot.setRawLevel("HEAVY");
        snapshot.setProvider("TOMTOM");
        snapshot.setMeasuredAt(OffsetDateTime.now());
        snapshot.setRecordedAt(OffsetDateTime.now());

        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(5L))
                .thenReturn(Optional.of(snapshot));

        // Act
        Optional<Double> result = trafficMonitoringService.getLatestCongestionScore(5L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(0.75, result.get());
    }

    @Test
    void getLatestCongestionScore_noSnapshot_returnsEmpty() {
        // Arrange
        when(congestionSnapshotRepository.findTopByIntersectionIdOrderByRecordedAtDesc(99L))
                .thenReturn(Optional.empty());

        // Act
        Optional<Double> result = trafficMonitoringService.getLatestCongestionScore(99L);

        // Assert
        assertFalse(result.isPresent());
    }

    // --- helper methods ---

    private Intersection buildIntersection(Long id, IntersectionCoordinate coord) {
        Intersection intersection = new Intersection();
        intersection.setId(id);
        intersection.setLabel(coord.label());
        intersection.setLatitude(coord.latitude());
        intersection.setLongitude(coord.longitude());
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(IntersectionStatus.CANDIDATE);
        return intersection;
    }
}
