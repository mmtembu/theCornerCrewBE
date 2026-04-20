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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 14: Snapshot Append-Only Monotonicity
 *
 * CongestionSnapshot records are never modified after insertion; measuredAt <= recordedAt for all snapshots.
 * This test verifies that snapshots are immutable and that timestamp ordering is maintained.
 *
 * <p><b>Validates: Requirements 12.9, 12.10</b></p>
 */
class SnapshotAppendOnlyMonotonicityPropertyTest {

    private static final double DEFAULT_THRESHOLD = 0.7;

    /**
     * Property: CongestionSnapshot records are never modified after insertion.
     * This test runs multiple poll cycles and verifies that previously persisted
     * snapshots remain unchanged.
     */
    @Property(tries = 20)
    void snapshotRecords_areNeverModifiedAfterInsertion(
            @ForAll("pollCycles") List<CongestionData> pollCycles,
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

        // Track all persisted snapshots
        List<CongestionSnapshot> persistedSnapshots = new ArrayList<>();

        // --- Configure mocks ---
        when(geoLocationService.resolveIntersections(any(BoundingBox.class)))
                .thenReturn(List.of(coordinate));
        when(intersectionRepository.findByLatitudeAndLongitude(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));
        when(snapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer((InvocationOnMock inv) -> {
                    CongestionSnapshot snapshot = inv.getArgument(0);
                    // Create a deep copy to track the state at insertion time
                    CongestionSnapshot copy = copySnapshot(snapshot);
                    persistedSnapshots.add(copy);
                    return snapshot;
                });

        // --- Create service ---
        TrafficMonitoringServiceImpl service = new TrafficMonitoringServiceImpl(
                geoLocationService,
                trafficApiAdapter,
                intersectionRepository,
                snapshotRepository,
                candidateService,
                properties,
                meterRegistry
        );

        // --- Execute multiple poll cycles ---
        for (int i = 0; i < pollCycles.size(); i++) {
            CongestionData data = pollCycles.get(i);
            when(trafficApiAdapter.getCongestionData(coordinate.latitude(), coordinate.longitude()))
                    .thenReturn(data);
            
            service.pollAndScore();
        }

        // --- PROPERTY: Verify snapshots were only saved (never updated) ---
        ArgumentCaptor<CongestionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(snapshotRepository, times(pollCycles.size())).save(snapshotCaptor.capture());
        
        // Verify no update operations were called
        verify(snapshotRepository, never()).saveAll(any());
        verify(snapshotRepository, never()).delete(any());
        verify(snapshotRepository, never()).deleteById(any());

        // --- PROPERTY: Verify each snapshot's immutability ---
        List<CongestionSnapshot> capturedSnapshots = snapshotCaptor.getAllValues();
        assertThat(capturedSnapshots)
                .as("Number of saved snapshots should equal number of poll cycles")
                .hasSize(pollCycles.size());

        // Verify that each snapshot was saved with correct data and never modified
        for (int i = 0; i < persistedSnapshots.size(); i++) {
            CongestionSnapshot original = persistedSnapshots.get(i);
            CongestionSnapshot captured = capturedSnapshots.get(i);
            
            assertThat(captured.getIntersectionId())
                    .as("Snapshot %d intersectionId should match original", i)
                    .isEqualTo(original.getIntersectionId());
            
            assertThat(captured.getScore())
                    .as("Snapshot %d score should match original", i)
                    .isEqualTo(original.getScore());
            
            assertThat(captured.getProvider())
                    .as("Snapshot %d provider should match original", i)
                    .isEqualTo(original.getProvider());
            
            assertThat(captured.getMeasuredAt())
                    .as("Snapshot %d measuredAt should match original", i)
                    .isEqualTo(original.getMeasuredAt());
        }
    }

    /**
     * Property: measuredAt <= recordedAt for all snapshots.
     * This test verifies that the timestamp when data was measured by the provider
     * is always before or equal to when our system recorded it.
     */
    @Property(tries = 20)
    void allSnapshots_haveMeasuredAtBeforeOrEqualRecordedAt(
            @ForAll("congestionData") CongestionData data,
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
                .thenReturn(data);
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

        // --- PROPERTY: measuredAt <= recordedAt ---
        assertThat(persistedSnapshot.getMeasuredAt())
                .as("measuredAt must be before or equal to recordedAt")
                .isBeforeOrEqualTo(persistedSnapshot.getRecordedAt());

        // --- PROPERTY: Both timestamps are non-null ---
        assertThat(persistedSnapshot.getMeasuredAt())
                .as("measuredAt must not be null")
                .isNotNull();
        
        assertThat(persistedSnapshot.getRecordedAt())
                .as("recordedAt must not be null")
                .isNotNull();
    }

    /**
     * Property: Multiple poll cycles create distinct snapshot records.
     * This test verifies that each poll cycle creates a new snapshot rather than
     * updating existing ones.
     */
    @Property(tries = 20)
    void multiplePollCycles_createDistinctSnapshots(
            @ForAll("pollCycles") List<CongestionData> pollCycles,
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
        when(intersectionRepository.findByLatitudeAndLongitude(coordinate.latitude(), coordinate.longitude()))
                .thenReturn(Optional.of(intersection));
        when(intersectionRepository.save(any(Intersection.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));
        when(snapshotRepository.save(any(CongestionSnapshot.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        // --- Create service ---
        TrafficMonitoringServiceImpl service = new TrafficMonitoringServiceImpl(
                geoLocationService,
                trafficApiAdapter,
                intersectionRepository,
                snapshotRepository,
                candidateService,
                properties,
                meterRegistry
        );

        // --- Execute multiple poll cycles ---
        for (CongestionData data : pollCycles) {
            when(trafficApiAdapter.getCongestionData(coordinate.latitude(), coordinate.longitude()))
                    .thenReturn(data);
            
            service.pollAndScore();
        }

        // --- PROPERTY: Verify exactly N snapshots were created for N poll cycles ---
        ArgumentCaptor<CongestionSnapshot> snapshotCaptor = ArgumentCaptor.forClass(CongestionSnapshot.class);
        verify(snapshotRepository, times(pollCycles.size())).save(snapshotCaptor.capture());

        List<CongestionSnapshot> capturedSnapshots = snapshotCaptor.getAllValues();
        assertThat(capturedSnapshots)
                .as("Should create exactly one snapshot per poll cycle")
                .hasSize(pollCycles.size());

        // --- PROPERTY: Each snapshot has distinct data from its poll cycle ---
        for (int i = 0; i < pollCycles.size(); i++) {
            CongestionData expectedData = pollCycles.get(i);
            CongestionSnapshot actualSnapshot = capturedSnapshots.get(i);
            
            double expectedScore = Math.max(0.0, Math.min(1.0, expectedData.score()));
            assertThat(actualSnapshot.getScore())
                    .as("Snapshot %d should have score from poll cycle %d", i, i)
                    .isEqualTo(expectedScore);
            
            assertThat(actualSnapshot.getRawLevel())
                    .as("Snapshot %d should have rawLevel from poll cycle %d", i, i)
                    .isEqualTo(expectedData.rawLevel());
        }
    }

    @Provide
    Arbitrary<CongestionData> congestionData() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 1.0).ofScale(3),
                Arbitraries.strings().alpha().ofLength(5),
                Arbitraries.longs().between(0L, System.currentTimeMillis())
        ).as((score, rawLevel, timestamp) -> 
                new CongestionData(score, rawLevel, Instant.ofEpochMilli(timestamp)));
    }

    @Provide
    Arbitrary<List<CongestionData>> pollCycles() {
        return congestionData().list().ofMinSize(2).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Long> intersectionIds() {
        return Arbitraries.longs().between(1L, 10_000L);
    }

    @Provide
    Arbitrary<IntersectionCoordinate> coordinates() {
        return Combinators.combine(
                Arbitraries.doubles().between(37.790, 37.810).ofScale(6),
                Arbitraries.doubles().between(-122.425, -122.400).ofScale(6),
                Arbitraries.strings().alpha().ofLength(10)
        ).as((lat, lng, label) -> new IntersectionCoordinate(lat, lng, "Intersection " + label));
    }

    private Intersection createIntersection(IntersectionStatus status) {
        Intersection intersection = new Intersection();
        intersection.setLabel("Test Intersection");
        intersection.setDescription("Test intersection for snapshot append-only testing");
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

    /**
     * Creates a deep copy of a CongestionSnapshot for tracking purposes.
     */
    private CongestionSnapshot copySnapshot(CongestionSnapshot original) {
        CongestionSnapshot copy = new CongestionSnapshot();
        copy.setId(original.getId());
        copy.setIntersectionId(original.getIntersectionId());
        copy.setScore(original.getScore());
        copy.setRawLevel(original.getRawLevel());
        copy.setProvider(original.getProvider());
        copy.setMeasuredAt(original.getMeasuredAt());
        copy.setRecordedAt(original.getRecordedAt());
        return copy;
    }
}
