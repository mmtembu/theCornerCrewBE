package com.cornercrew.app.incident;

import com.cornercrew.app.common.TrafficApiUnavailableException;
import com.cornercrew.app.config.IncidentProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.*;
import com.cornercrew.app.traffic.BoundingBox;
import com.cornercrew.app.traffic.RawTrafficIncident;
import com.cornercrew.app.traffic.TrafficApiAdapter;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Property tests for the Traffic Incident module.
 *
 * <p><b>Validates: Requirements 8.3, 8.4, 8.5, 8.6, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6</b></p>
 */
class TrafficIncidentPropertyTest {

    // ---- Helpers ----

    private Intersection makeIntersection(long id, String label, double lat, double lng) {
        Intersection i = new Intersection();
        i.setId(id);
        i.setLabel(label);
        i.setLatitude(lat);
        i.setLongitude(lng);
        i.setType(IntersectionType.FOUR_WAY_STOP);
        i.setStatus(IntersectionStatus.CONFIRMED);
        i.setCongestionScore(0.5);
        return i;
    }

    private IncidentProperties defaultIncidentProperties() {
        IncidentProperties props = new IncidentProperties();
        props.setMaxRadiusKm(50.0);
        props.setLabelProximityMeters(200.0);
        return props;
    }

    private TrafficMonitoringProperties defaultTrafficProperties(double freeFlowSpeedKmh) {
        TrafficMonitoringProperties props = new TrafficMonitoringProperties();
        props.setFreeFlowSpeedKmh(freeFlowSpeedKmh);
        TrafficMonitoringProperties.Neighborhood neighborhood = new TrafficMonitoringProperties.Neighborhood();
        neighborhood.setSouthLat(40.0);
        neighborhood.setWestLng(-74.0);
        neighborhood.setNorthLat(41.0);
        neighborhood.setEastLng(-73.0);
        props.setNeighborhood(neighborhood);
        return props;
    }

    // ---- Property 19: Traffic Incident Enrichment and Clamping ----

    /**
     * Property 19: Traffic Incident Enrichment and Clamping
     *
     * For any raw traffic incident with arbitrary speed and delay values (including extreme values):
     * (a) if a known intersection exists within 200m, the label is that intersection's label;
     *     otherwise the label is the roadName from the API.
     * (b) averageSpeedKmh is clamped to [0, 200].
     * (c) estimatedDelayMinutes is clamped to [0, 1440].
     * (d) estimatedDelayMinutes == ceil(delaySeconds / 60.0), then clamped.
     *
     * <p><b>Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5, 9.6</b></p>
     */
    @Property(tries = 10)
    void enrichment_clampsSpeedAndDelay_andAssignsCorrectLabel(
            @ForAll("speeds") double currentSpeedKmh,
            @ForAll("delaySeconds") int delaySeconds,
            @ForAll("booleans") boolean hasNearbyIntersection
    ) {
        // Fixed location for the incident
        double incidentLat = 40.7128;
        double incidentLng = -74.0060;
        String roadName = "Broadway";
        String intersectionLabel = "Main & 5th";

        RawTrafficIncident raw = new RawTrafficIncident(
                "inc-1", incidentLat, incidentLng, roadName,
                Instant.now(), currentSpeedKmh, delaySeconds
        );

        // Build intersection list: if hasNearbyIntersection, place one very close (same coords)
        // otherwise place one far away
        Intersection intersection;
        if (hasNearbyIntersection) {
            // Within 200m — same coordinates
            intersection = makeIntersection(1L, intersectionLabel, incidentLat, incidentLng);
        } else {
            // Far away — 10 degrees off
            intersection = makeIntersection(1L, intersectionLabel, incidentLat + 10.0, incidentLng + 10.0);
        }

        TrafficApiAdapter trafficApiAdapter = mock(TrafficApiAdapter.class);
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class))).thenReturn(List.of(raw));

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAll()).thenReturn(List.of(intersection));

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);

        TrafficIncidentServiceImpl service = new TrafficIncidentServiceImpl(
                trafficApiAdapter, intersectionRepo, snapshotRepo,
                defaultIncidentProperties(), defaultTrafficProperties(60.0)
        );

        List<TrafficIncidentDto> result = service.getIncidents(incidentLat, incidentLng, 5.0);

        assertThat(result).hasSize(1);
        TrafficIncidentDto dto = result.get(0);

        // (a) Label assignment
        if (hasNearbyIntersection) {
            assertThat(dto.label())
                    .as("Label should be intersection label when within 200m")
                    .isEqualTo(intersectionLabel);
        } else {
            assertThat(dto.label())
                    .as("Label should be road name when no intersection within 200m")
                    .isEqualTo(roadName);
        }

        // (b) Speed clamped to [0, 200]
        assertThat(dto.averageSpeedKmh())
                .as("averageSpeedKmh should be clamped to [0, 200]")
                .isBetween(0.0, 200.0);

        // (c) Delay clamped to [0, 1440]
        assertThat(dto.estimatedDelayMinutes())
                .as("estimatedDelayMinutes should be clamped to [0, 1440]")
                .isBetween(0, 1440);

        // (d) estimatedDelayMinutes == clamp(ceil(delaySeconds / 60.0), 0, 1440)
        int expectedDelay = (int) Math.ceil(delaySeconds / 60.0);
        expectedDelay = Math.max(0, Math.min(1440, expectedDelay));
        assertThat(dto.estimatedDelayMinutes())
                .as("estimatedDelayMinutes should equal ceil(delaySeconds/60) clamped to [0,1440]")
                .isEqualTo(expectedDelay);
    }

    // ---- Property 4: Estimated Delay Computation (formatting) ----

    /**
     * Property 4: Estimated Delay Computation
     *
     * For any delay minutes in [0, 1500]:
     * - if >= 60, formatted string matches "{hours}h {minutes}m"
     * - if < 60, formatted string matches "{minutes} min"
     *
     * <p><b>Validates: Requirements 8.4, 8.5</b></p>
     */
    @Property(tries = 10)
    void formatDelay_producesCorrectFormat(
            @ForAll @IntRange(min = 0, max = 1500) int delayMinutes
    ) {
        String formatted = TrafficIncidentServiceImpl.formatDelay(delayMinutes);

        if (delayMinutes >= 60) {
            int expectedHours = delayMinutes / 60;
            int expectedMinutes = delayMinutes % 60;
            String expected = expectedHours + "h " + expectedMinutes + "m";
            assertThat(formatted)
                    .as("Delay of %d minutes should format as '%s'", delayMinutes, expected)
                    .isEqualTo(expected);
        } else {
            String expected = delayMinutes + " min";
            assertThat(formatted)
                    .as("Delay of %d minutes should format as '%s'", delayMinutes, expected)
                    .isEqualTo(expected);
        }
    }

    // ---- Property 20: Fallback Speed Derivation ----

    /**
     * Property 20: Fallback Speed Derivation
     *
     * For any congestionScore in [0.0, 1.0] and freeFlowSpeedKmh > 0:
     * the fallback averageSpeedKmh should equal freeFlowSpeedKmh * (1 - congestionScore),
     * clamped to [0, 200]. The result should be non-negative.
     *
     * <p><b>Validates: Requirements 8.6</b></p>
     */
    @Property(tries = 10)
    void fallbackSpeed_derivedFromCongestionScore(
            @ForAll("congestionScores") double congestionScore,
            @ForAll("freeFlowSpeeds") double freeFlowSpeedKmh
    ) {
        double incidentLat = 40.7128;
        double incidentLng = -74.0060;

        // Set up intersection within the bounding box
        Intersection intersection = makeIntersection(1L, "Test Intersection", incidentLat, incidentLng);

        // Set up congestion snapshot with the generated score
        CongestionSnapshot snapshot = new CongestionSnapshot();
        snapshot.setId(1L);
        snapshot.setIntersectionId(1L);
        snapshot.setScore(congestionScore);
        snapshot.setProvider("test");
        snapshot.setMeasuredAt(OffsetDateTime.now(ZoneOffset.UTC));
        snapshot.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Mock TrafficApiAdapter to throw TrafficApiUnavailableException
        TrafficApiAdapter trafficApiAdapter = mock(TrafficApiAdapter.class);
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class)))
                .thenThrow(new TrafficApiUnavailableException("API unavailable"));

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAll()).thenReturn(List.of(intersection));

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findTopByIntersectionIdOrderByRecordedAtDesc(1L))
                .thenReturn(Optional.of(snapshot));

        TrafficIncidentServiceImpl service = new TrafficIncidentServiceImpl(
                trafficApiAdapter, intersectionRepo, snapshotRepo,
                defaultIncidentProperties(), defaultTrafficProperties(freeFlowSpeedKmh)
        );

        List<TrafficIncidentDto> result = service.getIncidents(incidentLat, incidentLng, 5.0);

        assertThat(result).hasSize(1);
        TrafficIncidentDto dto = result.get(0);

        // Expected: freeFlowSpeedKmh * (1 - congestionScore), clamped to [0, 200]
        double expectedSpeed = freeFlowSpeedKmh * (1.0 - congestionScore);
        expectedSpeed = Math.max(0, Math.min(200, expectedSpeed));

        assertThat(dto.averageSpeedKmh())
                .as("Fallback speed should equal freeFlowSpeedKmh * (1 - congestionScore) clamped to [0, 200]")
                .isCloseTo(expectedSpeed, org.assertj.core.data.Offset.offset(0.01));

        assertThat(dto.averageSpeedKmh())
                .as("Fallback speed should be non-negative")
                .isGreaterThanOrEqualTo(0.0);
    }

    // ---- Property 22: Traffic Incident Response Completeness ----

    /**
     * Property 22: Traffic Incident Response Completeness
     *
     * For any traffic incident returned by the incidents endpoint, the response should include
     * non-null id, label, startedAt, estimatedDelayFormatted. averageSpeedKmh >= 0 and
     * estimatedDelayMinutes >= 0.
     *
     * <p><b>Validates: Requirements 8.3, 9.6</b></p>
     */
    @Property(tries = 10)
    void responseCompleteness_allFieldsPresentAndNonNegative(
            @ForAll("incidentDataList") List<IncidentTestData> incidentDataList
    ) {
        double centerLat = 40.7128;
        double centerLng = -74.0060;

        List<RawTrafficIncident> rawIncidents = incidentDataList.stream()
                .map(data -> new RawTrafficIncident(
                        data.id, centerLat + data.latOffset, centerLng + data.lngOffset,
                        data.roadName, Instant.now(), data.speedKmh, data.delaySeconds
                ))
                .toList();

        TrafficApiAdapter trafficApiAdapter = mock(TrafficApiAdapter.class);
        when(trafficApiAdapter.getIncidentData(any(BoundingBox.class))).thenReturn(rawIncidents);

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAll()).thenReturn(List.of());

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);

        TrafficIncidentServiceImpl service = new TrafficIncidentServiceImpl(
                trafficApiAdapter, intersectionRepo, snapshotRepo,
                defaultIncidentProperties(), defaultTrafficProperties(60.0)
        );

        List<TrafficIncidentDto> result = service.getIncidents(centerLat, centerLng, 5.0);

        assertThat(result).hasSameSizeAs(rawIncidents);

        for (TrafficIncidentDto dto : result) {
            assertThat(dto.id()).as("id should be non-null").isNotNull();
            assertThat(dto.label()).as("label should be non-null").isNotNull();
            assertThat(dto.startedAt()).as("startedAt should be non-null").isNotNull();
            assertThat(dto.estimatedDelayFormatted()).as("estimatedDelayFormatted should be non-null").isNotNull();
            assertThat(dto.averageSpeedKmh())
                    .as("averageSpeedKmh should be >= 0")
                    .isGreaterThanOrEqualTo(0.0);
            assertThat(dto.estimatedDelayMinutes())
                    .as("estimatedDelayMinutes should be >= 0")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    // ---- Providers ----

    @Provide
    Arbitrary<Boolean> booleans() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Double> speeds() {
        return Arbitraries.doubles().between(-50.0, 500.0);
    }

    @Provide
    Arbitrary<Integer> delaySeconds() {
        return Arbitraries.integers().between(-100, 200000);
    }

    @Provide
    Arbitrary<Double> congestionScores() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Double> freeFlowSpeeds() {
        return Arbitraries.doubles().between(1.0, 300.0);
    }

    @Provide
    Arbitrary<List<IncidentTestData>> incidentDataList() {
        AtomicLong idGen = new AtomicLong(1);
        Arbitrary<IncidentTestData> single = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.doubles().between(-0.01, 0.01),
                Arbitraries.doubles().between(-0.01, 0.01),
                Arbitraries.doubles().between(-50.0, 500.0),
                Arbitraries.integers().between(0, 200000)
        ).as((roadName, latOff, lngOff, speed, delay) ->
                new IncidentTestData("inc-" + idGen.getAndIncrement(), roadName, latOff, lngOff, speed, delay)
        );
        return single.list().ofMinSize(1).ofMaxSize(5);
    }

    // ---- Test data record ----

    record IncidentTestData(String id, String roadName, double latOffset, double lngOffset,
                            double speedKmh, int delaySeconds) {}
}
