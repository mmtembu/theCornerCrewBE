package com.cornercrew.app.intersection;

import com.cornercrew.app.campaign.CampaignService;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.geolocation.BoundingBox;
import com.cornercrew.app.geolocation.GeoLocationService;
import com.cornercrew.app.geolocation.IntersectionCoordinate;
import com.cornercrew.app.traffic.CongestionData;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.mockito.invocation.InvocationOnMock;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 10: Congestion Threshold Invariant
 *
 * If score >= threshold then intersection status in {FLAGGED, CONFIRMED, DISMISSED};
 * score < threshold never auto-flags.
 *
 * <p><b>Validates: Requirements 8.5, 8.6</b></p>
 */
class CongestionThresholdInvariantPropertyTest {

    private static final double DEFAULT_THRESHOLD = 0.7;

    @Property(tries = 20)
    void scoreAboveThreshold_candidateIntersection_getsFlagged(
            @ForAll("scoresAboveThreshold") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection with CANDIDATE status ---
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
                .thenReturn(new CongestionData(score, "HEAVY", Instant.now()));
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

        // --- Verify flagging was triggered ---
        verify(candidateService, times(1)).flagIfNotAlready(intersectionId);

        // --- Verify intersection score was updated ---
        assertThat(intersection.getCongestionScore())
                .as("Intersection congestion score should be updated to %f", score)
                .isEqualTo(score);
    }

    @Property(tries = 20)
    void scoreBelowThreshold_candidateIntersection_doesNotGetFlagged(
            @ForAll("scoresBelowThreshold") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection with CANDIDATE status ---
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
                .thenReturn(new CongestionData(score, "LIGHT", Instant.now()));
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

        // --- Verify flagging was NOT triggered ---
        verify(candidateService, never()).flagIfNotAlready(anyLong());

        // --- Verify intersection score was still updated ---
        assertThat(intersection.getCongestionScore())
                .as("Intersection congestion score should be updated to %f", score)
                .isEqualTo(score);

        // --- Verify status remains CANDIDATE ---
        assertThat(intersection.getStatus())
                .as("Intersection status should remain CANDIDATE when score < threshold")
                .isEqualTo(IntersectionStatus.CANDIDATE);
    }

    @Property(tries = 20)
    void scoreAtExactThreshold_candidateIntersection_getsFlagged(
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        double score = DEFAULT_THRESHOLD; // Exactly at threshold

        // --- Set up intersection with CANDIDATE status ---
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
                .thenReturn(new CongestionData(score, "MODERATE", Instant.now()));
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

        // --- Verify flagging was triggered (>= threshold) ---
        verify(candidateService, times(1)).flagIfNotAlready(intersectionId);

        // --- Verify intersection score was updated ---
        assertThat(intersection.getCongestionScore())
                .as("Intersection congestion score should be updated to threshold value")
                .isEqualTo(DEFAULT_THRESHOLD);
    }

    @Property(tries = 20)
    void scoreAboveThreshold_alreadyFlaggedIntersection_remainsFlagged(
            @ForAll("scoresAboveThreshold") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection with FLAGGED status ---
        Intersection intersection = createIntersection(IntersectionStatus.FLAGGED);
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
                .thenReturn(new CongestionData(score, "HEAVY", Instant.now()));
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

        // --- Verify flagging was still called (idempotent) ---
        verify(candidateService, times(1)).flagIfNotAlready(intersectionId);

        // --- Verify status remains FLAGGED ---
        assertThat(intersection.getStatus())
                .as("Intersection status should remain FLAGGED")
                .isEqualTo(IntersectionStatus.FLAGGED);
    }

    @Property(tries = 20)
    void scoreAboveThreshold_confirmedIntersection_remainsConfirmed(
            @ForAll("scoresAboveThreshold") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection with CONFIRMED status ---
        Intersection intersection = createIntersection(IntersectionStatus.CONFIRMED);
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
                .thenReturn(new CongestionData(score, "HEAVY", Instant.now()));
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

        // --- Verify flagging was still called (idempotent) ---
        verify(candidateService, times(1)).flagIfNotAlready(intersectionId);

        // --- Verify status remains CONFIRMED ---
        assertThat(intersection.getStatus())
                .as("Intersection status should remain CONFIRMED")
                .isEqualTo(IntersectionStatus.CONFIRMED);
    }

    @Property(tries = 20)
    void scoreAboveThreshold_dismissedIntersection_remainsDismissed(
            @ForAll("scoresAboveThreshold") double score,
            @ForAll("intersectionIds") Long intersectionId,
            @ForAll("coordinates") IntersectionCoordinate coordinate
    ) {
        // --- Set up intersection with DISMISSED status ---
        Intersection intersection = createIntersection(IntersectionStatus.DISMISSED);
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
                .thenReturn(new CongestionData(score, "HEAVY", Instant.now()));
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

        // --- Verify flagging was still called (idempotent) ---
        verify(candidateService, times(1)).flagIfNotAlready(intersectionId);

        // --- Verify status remains DISMISSED ---
        assertThat(intersection.getStatus())
                .as("Intersection status should remain DISMISSED")
                .isEqualTo(IntersectionStatus.DISMISSED);
    }

    @Provide
    Arbitrary<Double> scoresAboveThreshold() {
        return Arbitraries.doubles()
                .between(DEFAULT_THRESHOLD, 1.0);
    }

    @Provide
    Arbitrary<Double> scoresBelowThreshold() {
        return Arbitraries.doubles()
                .between(0.0, DEFAULT_THRESHOLD)
                .filter(score -> score < DEFAULT_THRESHOLD); // Exclude exact threshold
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<IntersectionCoordinate> coordinates() {
        return Combinators.combine(
                Arbitraries.doubles().between(37.790, 37.810).ofScale(3), // latitude within neighborhood
                Arbitraries.doubles().between(-122.425, -122.400).ofScale(3), // longitude within neighborhood
                Arbitraries.strings().alpha().ofLength(10)
        ).as((lat, lng, label) -> new IntersectionCoordinate(lat, lng, "Intersection " + label));
    }

    private Intersection createIntersection(IntersectionStatus status) {
        Intersection intersection = new Intersection();
        intersection.setLabel("Test Intersection");
        intersection.setDescription("Test intersection for threshold testing");
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
