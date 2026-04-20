package com.cornercrew.app.intersection;

import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.geolocation.BoundingBox;
import com.cornercrew.app.geolocation.GeoLocationService;
import com.cornercrew.app.geolocation.IntersectionCoordinate;
import com.cornercrew.app.traffic.CongestionData;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 12: Congestion Score Bounds
 *
 * All persisted CongestionSnapshot scores and intersection congestionScores are in [0.0, 1.0].
 * The system enforces this through CongestionData validation and defensive clamping in the service.
 *
 * <p><b>Validates: Requirements 8.10, 11.7, 12.9</b></p>
 */
class CongestionScoreBoundsPropertyTest {

    private static final double DEFAULT_THRESHOLD = 0.7;

    /**
     * Property: All persisted scores are within [0.0, 1.0] bounds.
     * This test verifies that for any valid score from the adapter,
     * the persisted snapshot and intersection scores remain within bounds.
     */
    @Property(tries = 20)
    void allPersistedScores_areWithinBounds(
            @ForAll("validScores") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection ---
        Intersection intersection = createIntersection(IntersectionStatus.CANDIDATE);
        intersection.setId(intersectionId);
        intersection.setLatitude(coordinate.latitude());
        intersection.setLongitude(coordinate.longitude());

        // --- Mock dependencies ---
        TrafficMonitoringProperties properties = createProperties(DEFAULT_THRESHOLD);
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CongestionSnapshotRepository snapshotRepository = mock(CongestionSnapshotRepository.class);
        GeoLocationService geoLocationService = mock(GeoLocationService.class);
        TrafficApiAdapter trafficApiAdapter = mock(TrafficApiAdapter.class);
        IntersectionCandidateService candidateService = mock(IntersectionCandidateService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // --- Configure mocks ---
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coordinate));
        when(trafficApiAdapter.getCongestionData(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(new CongestionData(score, "TEST", Instant.now()));
        when(intersectionRepository.findByLatitudeAndLongitude(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));
        when(snapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        // --- Execute pollAndScore ---
        TrafficMonitoringServiceImpl service = new TrafficMonitoringServiceImpl(
                geoLocationService,
                trafficApiAdapter,
                intersectionRepository,
                snapshotRepository,
                candidateService,
                properties,
                meterRegistry
        );

        service.pollAndScore();

        // --- Capture persisted snapshot ---
        ArgumentCaptor<CongestionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());
        CongestionSnapshot persistedSnapshot = snapshotCaptor.getValue();

        // --- PROPERTY: Persisted snapshot score is within [0.0, 1.0] ---
        assertThat(persistedSnapshot.getScore())
                .as("CongestionSnapshot score must be within [0.0, 1.0]")
                .isBetween(0.0, 1.0);

        // --- PROPERTY: Intersection congestionScore is within [0.0, 1.0] ---
        assertThat(intersection.getCongestionScore())
                .as("Intersection congestionScore must be within [0.0, 1.0]")
                .isBetween(0.0, 1.0);

        // --- PROPERTY: Score is preserved exactly (no unnecessary modification) ---
        assertThat(persistedSnapshot.getScore())
                .as("Valid score should be preserved exactly")
                .isEqualTo(score);
        assertThat(intersection.getCongestionScore())
                .as("Valid score should be preserved exactly")
                .isEqualTo(score);
    }

    /**
     * Property: Boundary values (0.0 and 1.0) are handled correctly.
     */
    @Property(tries = 20)
    void boundaryValues_areHandledCorrectly(
            @ForAll("boundaryScores") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection ---
        Intersection intersection = createIntersection(IntersectionStatus.CANDIDATE);
        intersection.setId(intersectionId);
        intersection.setLatitude(coordinate.latitude());
        intersection.setLongitude(coordinate.longitude());

        // --- Mock dependencies ---
        TrafficMonitoringProperties properties = createProperties(DEFAULT_THRESHOLD);
        IntersectionRepository intersectionRepository = mock(IntersectionRepository.class);
        CongestionSnapshotRepository snapshotRepository = mock(CongestionSnapshotRepository.class);
        GeoLocationService geoLocationService = mock(GeoLocationService.class);
        TrafficApiAdapter trafficApiAdapter = mock(TrafficApiAdapter.class);
        IntersectionCandidateService candidateService = mock(IntersectionCandidateService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // --- Configure mocks ---
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coordinate));
        when(trafficApiAdapter.getCongestionData(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(new CongestionData(score, "BOUNDARY", Instant.now()));
        when(intersectionRepository.findByLatitudeAndLongitude(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));
        when(snapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        // --- Execute pollAndScore ---
        TrafficMonitoringServiceImpl service = new TrafficMonitoringServiceImpl(
                geoLocationService,
                trafficApiAdapter,
                intersectionRepository,
                snapshotRepository,
                candidateService,
                properties,
                meterRegistry
        );

        service.pollAndScore();

        // --- Capture persisted snapshot ---
        ArgumentCaptor<CongestionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(snapshotRepository, times(1)).save(snapshotCaptor.capture());
        CongestionSnapshot persistedSnapshot = snapshotCaptor.getValue();

        // --- PROPERTY: Boundary values are within bounds ---
        assertThat(persistedSnapshot.getScore())
                .as("CongestionSnapshot score must be within [0.0, 1.0]")
                .isBetween(0.0, 1.0);

        assertThat(intersection.getCongestionScore())
                .as("Intersection congestionScore must be within [0.0, 1.0]")
                .isBetween(0.0, 1.0);

        // --- PROPERTY: Exact boundary values are preserved ---
        assertThat(persistedSnapshot.getScore())
                .as("Boundary value %f should be preserved exactly", score)
                .isEqualTo(score);
    }

    @Provide
    Arbitrary<Double> validScores() {
        return Arbitraries.doubles()
                .between(0.0, 1.0)
                .ofScale(3);
    }

    @Provide
    Arbitrary<Double> boundaryScores() {
        return Arbitraries.of(0.0, 1.0, 0.0001, 0.9999);
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<IntersectionCoordinate> coordinates() {
        return Combinators.combine(
                Arbitraries.doubles().between(37.790, 37.810).ofScale(3),
                Arbitraries.doubles().between(-122.425, -122.400).ofScale(3),
                Arbitraries.strings().alpha().ofLength(10)
        ).as((lat, lng, label) -> new IntersectionCoordinate(lat, lng, "Intersection " + label));
    }

    private Intersection createIntersection(IntersectionStatus status) {
        Intersection intersection = new Intersection();
        intersection.setLabel("Test Intersection");
        intersection.setDescription("Test intersection for score bounds testing");
        intersection.setLatitude(37.7749);
        intersection.setLongitude(-122.4194);
        intersection.setType(IntersectionType.FOUR_WAY_STOP);
        intersection.setStatus(status);
        intersection.setCongestionScore(0.5);
        intersection.setLastCheckedAt(OffsetDateTime.now());
        return intersection;
    }

    private TrafficMonitoringProperties createProperties(double threshold) {
        TrafficMonitoringProperties properties = new TrafficMonitoringProperties();
        properties.setProvider("TOMTOM");
        properties.setCongestionThreshold(threshold);
        properties.setPollingIntervalMs(300000);

        TrafficMonitoringProperties.Neighborhood neighborhood = new TrafficMonitoringProperties.Neighborhood();
        neighborhood.setSouthLat(37.790);
        neighborhood.setWestLng(-122.425);
        neighborhood.setNorthLat(37.810);
        neighborhood.setEastLng(-122.400);
        properties.setNeighborhood(neighborhood);

        return properties;
    }
}
