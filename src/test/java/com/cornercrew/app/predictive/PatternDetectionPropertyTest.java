package com.cornercrew.app.predictive;

import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.config.PredictiveProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for pattern detection in PredictiveCampaignServiceImpl.
 *
 * <p><b>Validates: Requirements 5.2, 6.1, 6.2, 6.3, 6.5, 6.6, 6.7</b></p>
 */
class PatternDetectionPropertyTest {

    private static final double THRESHOLD = 0.7;
    private static final int MIN_OCCURRENCES = 3;
    private static final int LOOKBACK_WEEKS = 4;
    private static final int BUCKET_SIZE_HOURS = 2;

    private PredictiveCampaignServiceImpl createService(
            CongestionSnapshotRepository snapshotRepo,
            IntersectionRepository intersectionRepo,
            RecurrencePatternRepository patternRepo) {

        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);

        PredictiveProperties predictiveProps = new PredictiveProperties();
        predictiveProps.setLookbackWeeks(LOOKBACK_WEEKS);
        predictiveProps.setMinOccurrences(MIN_OCCURRENCES);

        TrafficMonitoringProperties trafficProps = new TrafficMonitoringProperties();
        trafficProps.setCongestionThreshold(THRESHOLD);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        return new PredictiveCampaignServiceImpl(
                snapshotRepo,
                patternRepo,
                intersectionRepo,
                campaignRepo,
                ciRepo,
                predictiveProps,
                trafficProps,
                objectMapper
        );
    }

    private CongestionSnapshot buildSnapshot(Long id, Long intersectionId, double score,
                                              DayOfWeek dayOfWeek, int bucketHour) {
        CongestionSnapshot s = new CongestionSnapshot();
        s.setId(id);
        s.setIntersectionId(intersectionId);
        s.setScore(score);
        s.setProvider("test");

        // Build a measuredAt within the lookback window on the given dayOfWeek and bucket hour
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime base = now.minusWeeks(1);
        // Adjust to the target day of week
        while (base.getDayOfWeek() != dayOfWeek) {
            base = base.minusDays(1);
        }
        // Set the hour within the bucket
        OffsetDateTime measuredAt = base.withHour(bucketHour).withMinute(15).withSecond(0).withNano(0);
        s.setMeasuredAt(measuredAt);
        s.setRecordedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return s;
    }

    private Intersection buildIntersection(Long id, String label) {
        Intersection i = new Intersection();
        i.setId(id);
        i.setLabel(label);
        i.setLatitude(40.0);
        i.setLongitude(-74.0);
        i.setType(IntersectionType.FOUR_WAY_STOP);
        i.setStatus(IntersectionStatus.CONFIRMED);
        return i;
    }

    private LocalTime toBucketStart(int hour) {
        int bucketStartHour = (hour / BUCKET_SIZE_HOURS) * BUCKET_SIZE_HOURS;
        return LocalTime.of(bucketStartHour, 0);
    }

    // -----------------------------------------------------------------------
    // Property 11: Recurrence Pattern Detection Threshold
    // -----------------------------------------------------------------------

    /**
     * Property 11: Recurrence Pattern Detection Threshold
     *
     * Generate a list of CongestionSnapshot records for a single
     * (intersectionId, dayOfWeek, timeBucket) group. Some with score >= threshold,
     * some below. Verify: a pattern is emitted iff the count of high-congestion
     * snapshots >= minOccurrences. Verify: the emitted pattern's occurrenceCount
     * equals the count of high-congestion snapshots.
     *
     * <p><b>Validates: Requirements 5.2, 6.2, 6.3</b></p>
     */
    @Property(tries = 10)
    void patternEmittedIffHighCongestionCountMeetsMinOccurrences(
            @ForAll("highScores") List<Double> highScores,
            @ForAll("lowScores") List<Double> lowScores,
            @ForAll("dayOfWeekArb") DayOfWeek dayOfWeek,
            @ForAll("bucketHourArb") int bucketHour
    ) {
        Long intersectionId = 1L;
        long idCounter = 1L;

        List<CongestionSnapshot> snapshots = new ArrayList<>();
        for (Double score : highScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }
        for (Double score : lowScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        Intersection intersection = buildIntersection(intersectionId, "Test Intersection");
        when(intersectionRepo.findAllById(any())).thenReturn(List.of(intersection));

        RecurrencePatternRepository patternRepo = mock(RecurrencePatternRepository.class);
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PredictiveCampaignServiceImpl service = createService(snapshotRepo, intersectionRepo, patternRepo);
        List<RecurrencePatternDto> patterns = service.detectPatterns();

        int highCount = highScores.size();

        if (highCount >= MIN_OCCURRENCES) {
            assertThat(patterns).hasSize(1);
            assertThat(patterns.get(0).occurrenceCount()).isEqualTo(highCount);
        } else {
            assertThat(patterns).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Property 12: Recurrence Pattern Average Score Invariant
    // -----------------------------------------------------------------------

    /**
     * Property 12: Recurrence Pattern Average Score Invariant
     *
     * Generate high-congestion snapshots (all scores >= threshold).
     * Verify: averageCongestionScore equals the arithmetic mean of those scores.
     * Verify: averageCongestionScore is in [threshold, 1.0].
     *
     * <p><b>Validates: Requirements 6.5, 6.6</b></p>
     */
    @Property(tries = 10)
    void averageScoreEqualsArithmeticMeanOfHighCongestionScores(
            @ForAll("highScoreLists") List<Double> highScores,
            @ForAll("dayOfWeekArb") DayOfWeek dayOfWeek,
            @ForAll("bucketHourArb") int bucketHour
    ) {
        Assume.that(highScores.size() >= MIN_OCCURRENCES);

        Long intersectionId = 1L;
        long idCounter = 1L;

        List<CongestionSnapshot> snapshots = new ArrayList<>();
        for (Double score : highScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        Intersection intersection = buildIntersection(intersectionId, "Test Intersection");
        when(intersectionRepo.findAllById(any())).thenReturn(List.of(intersection));

        RecurrencePatternRepository patternRepo = mock(RecurrencePatternRepository.class);
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PredictiveCampaignServiceImpl service = createService(snapshotRepo, intersectionRepo, patternRepo);
        List<RecurrencePatternDto> patterns = service.detectPatterns();

        assertThat(patterns).hasSize(1);

        double expectedAvg = highScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        assertThat(patterns.get(0).averageCongestionScore()).isCloseTo(expectedAvg, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(patterns.get(0).averageCongestionScore()).isGreaterThanOrEqualTo(THRESHOLD);
        assertThat(patterns.get(0).averageCongestionScore()).isLessThanOrEqualTo(1.0);
    }

    // -----------------------------------------------------------------------
    // Property 13: Snapshot Grouping Correctness
    // -----------------------------------------------------------------------

    /**
     * Property 13: Snapshot Grouping Correctness
     *
     * Generate snapshots with different (intersectionId, dayOfWeek, time) combinations.
     * Verify: each snapshot is assigned to exactly one group (by checking the patterns
     * emitted match expected groups).
     *
     * <p><b>Validates: Requirements 6.1</b></p>
     */
    @Property(tries = 10)
    void eachSnapshotAssignedToExactlyOneGroup(
            @ForAll("multiGroupSnapshots") List<SnapshotSpec> specs
    ) {
        Assume.that(!specs.isEmpty());

        long idCounter = 1L;
        List<CongestionSnapshot> snapshots = new ArrayList<>();
        Set<Long> intersectionIds = new HashSet<>();

        for (SnapshotSpec spec : specs) {
            CongestionSnapshot s = buildSnapshot(idCounter++, spec.intersectionId, spec.score,
                    spec.dayOfWeek, spec.hour);
            snapshots.add(s);
            intersectionIds.add(spec.intersectionId);
        }

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);

        List<Intersection> intersections = intersectionIds.stream()
                .map(id -> buildIntersection(id, "Intersection #" + id))
                .toList();
        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAllById(any())).thenReturn(intersections);

        RecurrencePatternRepository patternRepo = mock(RecurrencePatternRepository.class);
        final long[] patternIdCounter = {100L};
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(patternIdCounter[0]++);
            return p;
        });

        PredictiveCampaignServiceImpl service = createService(snapshotRepo, intersectionRepo, patternRepo);
        List<RecurrencePatternDto> patterns = service.detectPatterns();

        // Compute expected groups: group by (intersectionId, dayOfWeek, bucketStart)
        // and count high-congestion snapshots per group
        Map<String, Long> expectedGroups = specs.stream()
                .filter(s -> s.score >= THRESHOLD)
                .collect(Collectors.groupingBy(
                        s -> s.intersectionId + "|" + s.dayOfWeek + "|" + toBucketStart(s.hour),
                        Collectors.counting()
                ));

        // Only groups with count >= MIN_OCCURRENCES should produce patterns
        long expectedPatternCount = expectedGroups.values().stream()
                .filter(count -> count >= MIN_OCCURRENCES)
                .count();

        assertThat(patterns).hasSize((int) expectedPatternCount);

        // Verify each emitted pattern corresponds to a valid group key
        for (RecurrencePatternDto pattern : patterns) {
            String key = pattern.intersectionId() + "|" + pattern.dayOfWeek() + "|" + pattern.timeBucketStart();
            assertThat(expectedGroups).containsKey(key);
            assertThat((long) pattern.occurrenceCount()).isEqualTo(expectedGroups.get(key));
        }
    }

    // -----------------------------------------------------------------------
    // Property 14: Pattern Detection Determinism
    // -----------------------------------------------------------------------

    /**
     * Property 14: Pattern Detection Determinism
     *
     * Run detectPatterns() twice on the same input.
     * Verify: identical results both times (same intersection IDs, days, time buckets,
     * counts, scores).
     *
     * <p><b>Validates: Requirements 6.7</b></p>
     */
    @Property(tries = 10)
    void detectPatternsIsDeterministic(
            @ForAll("highScoreLists") List<Double> highScores,
            @ForAll("dayOfWeekArb") DayOfWeek dayOfWeek,
            @ForAll("bucketHourArb") int bucketHour
    ) {
        Assume.that(highScores.size() >= MIN_OCCURRENCES);

        Long intersectionId = 1L;
        long idCounter = 1L;

        List<CongestionSnapshot> snapshots = new ArrayList<>();
        for (Double score : highScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }

        Intersection intersection = buildIntersection(intersectionId, "Test Intersection");

        // First run
        CongestionSnapshotRepository snapshotRepo1 = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo1.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);
        IntersectionRepository intersectionRepo1 = mock(IntersectionRepository.class);
        when(intersectionRepo1.findAllById(any())).thenReturn(List.of(intersection));
        RecurrencePatternRepository patternRepo1 = mock(RecurrencePatternRepository.class);
        when(patternRepo1.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PredictiveCampaignServiceImpl service1 = createService(snapshotRepo1, intersectionRepo1, patternRepo1);
        List<RecurrencePatternDto> result1 = service1.detectPatterns();

        // Second run with same data
        CongestionSnapshotRepository snapshotRepo2 = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo2.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);
        IntersectionRepository intersectionRepo2 = mock(IntersectionRepository.class);
        when(intersectionRepo2.findAllById(any())).thenReturn(List.of(intersection));
        RecurrencePatternRepository patternRepo2 = mock(RecurrencePatternRepository.class);
        when(patternRepo2.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PredictiveCampaignServiceImpl service2 = createService(snapshotRepo2, intersectionRepo2, patternRepo2);
        List<RecurrencePatternDto> result2 = service2.detectPatterns();

        // Verify identical results
        assertThat(result1).hasSameSizeAs(result2);
        for (int i = 0; i < result1.size(); i++) {
            RecurrencePatternDto p1 = result1.get(i);
            RecurrencePatternDto p2 = result2.get(i);
            assertThat(p1.intersectionId()).isEqualTo(p2.intersectionId());
            assertThat(p1.dayOfWeek()).isEqualTo(p2.dayOfWeek());
            assertThat(p1.timeBucketStart()).isEqualTo(p2.timeBucketStart());
            assertThat(p1.timeBucketEnd()).isEqualTo(p2.timeBucketEnd());
            assertThat(p1.occurrenceCount()).isEqualTo(p2.occurrenceCount());
            assertThat(p1.averageCongestionScore()).isCloseTo(p2.averageCongestionScore(),
                    org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    // -----------------------------------------------------------------------
    // Arbitraries / Providers
    // -----------------------------------------------------------------------

    /** Scores at or above the threshold (high congestion). */
    @Provide
    Arbitrary<List<Double>> highScores() {
        return Arbitraries.doubles().between(THRESHOLD, 1.0)
                .list().ofMinSize(0).ofMaxSize(8);
    }

    /** Scores below the threshold (low congestion). */
    @Provide
    Arbitrary<List<Double>> lowScores() {
        return Arbitraries.doubles().between(0.0, THRESHOLD - 0.01)
                .list().ofMinSize(0).ofMaxSize(5);
    }

    /** Lists of high scores guaranteed to have at least MIN_OCCURRENCES entries. */
    @Provide
    Arbitrary<List<Double>> highScoreLists() {
        return Arbitraries.doubles().between(THRESHOLD, 1.0)
                .list().ofMinSize(MIN_OCCURRENCES).ofMaxSize(10);
    }

    @Provide
    Arbitrary<DayOfWeek> dayOfWeekArb() {
        return Arbitraries.of(DayOfWeek.values());
    }

    /** Even hours that align with 2-hour bucket starts: 0, 2, 4, ..., 22. */
    @Provide
    Arbitrary<Integer> bucketHourArb() {
        return Arbitraries.of(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22);
    }

    /** Snapshot specifications for multi-group testing. */
    @Provide
    Arbitrary<List<SnapshotSpec>> multiGroupSnapshots() {
        Arbitrary<SnapshotSpec> specArb = Combinators.combine(
                Arbitraries.longs().between(1L, 3L),       // intersectionId
                Arbitraries.of(DayOfWeek.values()),          // dayOfWeek
                Arbitraries.of(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22), // hour
                Arbitraries.doubles().between(0.5, 1.0)      // score (mix of high and low)
        ).as(SnapshotSpec::new);

        return specArb.list().ofMinSize(3).ofMaxSize(20);
    }

    /** Helper record for generating snapshot specifications. */
    record SnapshotSpec(Long intersectionId, DayOfWeek dayOfWeek, int hour, double score) {}
}
