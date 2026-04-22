package com.cornercrew.app.predictive;

import com.cornercrew.app.campaign.*;
import com.cornercrew.app.config.PredictiveProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for campaign drafting in PredictiveCampaignServiceImpl.
 *
 * <p><b>Validates: Requirements 5.3, 5.4, 5.5, 5.11</b></p>
 */
class CampaignDraftingPropertyTest {

    private static final double THRESHOLD = 0.7;
    private static final int MIN_OCCURRENCES = 3;
    private static final int LOOKBACK_WEEKS = 4;
    private static final int BUCKET_SIZE_HOURS = 2;
    private static final BigDecimal DEFAULT_TARGET = new BigDecimal("5000.00");

    private PredictiveCampaignServiceImpl createService(
            CongestionSnapshotRepository snapshotRepo,
            IntersectionRepository intersectionRepo,
            RecurrencePatternRepository patternRepo,
            CampaignRepository campaignRepo,
            CampaignIntersectionRepository ciRepo) {

        PredictiveProperties predictiveProps = new PredictiveProperties();
        predictiveProps.setLookbackWeeks(LOOKBACK_WEEKS);
        predictiveProps.setMinOccurrences(MIN_OCCURRENCES);
        predictiveProps.setDefaultTargetAmount(DEFAULT_TARGET);

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

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime base = now.minusWeeks(1);
        while (base.getDayOfWeek() != dayOfWeek) {
            base = base.minusDays(1);
        }
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
    // Property 15: Predictive Draft Idempotence
    // -----------------------------------------------------------------------

    /**
     * Property 15: Predictive Draft Idempotence
     *
     * Set up snapshots that produce at least one pattern (enough high-congestion
     * snapshots). Mock repositories so detectAndDraftCampaigns() creates a DRAFT
     * campaign on first run. On second run, the campaign created in the first run
     * should be found by the overlap check, so no new campaign is created.
     * Verify: campaignRepository.save() is called exactly once across both runs.
     *
     * <p><b>Validates: Requirements 5.3, 5.11</b></p>
     */
    @Property(tries = 10)
    void noDuplicateCampaignsWhenRunTwiceOnSamePatterns(
            @ForAll("dayOfWeekArb") DayOfWeek dayOfWeek,
            @ForAll("bucketHourArb") int bucketHour,
            @ForAll("highScoreLists") List<Double> highScores
    ) {
        Long intersectionId = 1L;
        String intersectionLabel = "Test Intersection";
        long idCounter = 1L;

        // Build enough high-congestion snapshots to trigger a pattern
        List<CongestionSnapshot> snapshots = new ArrayList<>();
        for (Double score : highScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }

        Intersection intersection = buildIntersection(intersectionId, intersectionLabel);

        // Shared mocks
        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAllById(any())).thenReturn(List.of(intersection));

        RecurrencePatternRepository patternRepo = mock(RecurrencePatternRepository.class);
        final long[] patternIdCounter = {1L};
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(patternIdCounter[0]++);
            return p;
        });

        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);

        // Track saved campaigns
        final Campaign[] savedCampaign = {null};
        AtomicLong campaignSaveCount = new AtomicLong(0);

        when(campaignRepo.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(100L);
            savedCampaign[0] = c;
            campaignSaveCount.incrementAndGet();
            return c;
        });

        when(ciRepo.save(any(CampaignIntersection.class))).thenAnswer(inv -> {
            CampaignIntersection ci = inv.getArgument(0);
            ci.setId(200L);
            return ci;
        });

        // First run: no existing campaigns, so overlap check returns empty
        when(ciRepo.findByIntersectionId(intersectionId)).thenReturn(Collections.emptyList());
        when(campaignRepo.findByStatusIn(any())).thenReturn(Collections.emptyList());

        PredictiveCampaignServiceImpl service = createService(
                snapshotRepo, intersectionRepo, patternRepo, campaignRepo, ciRepo);

        service.detectAndDraftCampaigns();

        // Verify first run created exactly one campaign
        assertThat(campaignSaveCount.get()).isEqualTo(1);

        // Second run: now the campaign from the first run exists
        // Mock the overlap check to find the created campaign
        CampaignIntersection link = new CampaignIntersection();
        link.setId(200L);
        link.setCampaignId(100L);
        link.setIntersectionId(intersectionId);
        when(ciRepo.findByIntersectionId(intersectionId)).thenReturn(List.of(link));

        Campaign existingCampaign = new Campaign();
        existingCampaign.setId(100L);
        existingCampaign.setStatus(CampaignStatus.DRAFT);
        // Compute the expected window for the overlap check
        LocalDate windowStart = LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        LocalDate windowEnd = windowStart.plusDays(1);
        existingCampaign.setWindowStart(windowStart);
        existingCampaign.setWindowEnd(windowEnd);
        when(campaignRepo.findByStatusIn(any())).thenReturn(List.of(existingCampaign));

        service.detectAndDraftCampaigns();

        // Verify: campaignRepository.save() was called exactly once across both runs
        assertThat(campaignSaveCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Property 16: Auto-Drafted Campaign Window Computation
    // -----------------------------------------------------------------------

    /**
     * Property 16: Auto-Drafted Campaign Window Computation
     *
     * Generate a DayOfWeek and time bucket. Set up snapshots that produce a pattern
     * for that day/bucket. Run detectAndDraftCampaigns(). Capture the saved Campaign
     * via ArgumentCaptor. Verify: windowStart is the next occurrence of the pattern's
     * dayOfWeek (on or after today), windowEnd is windowStart + 1 day, and title
     * matches the expected format.
     *
     * <p><b>Validates: Requirements 5.4, 5.5</b></p>
     */
    @Property(tries = 10)
    void autoDraftedCampaignHasCorrectWindowAndTitle(
            @ForAll("dayOfWeekArb") DayOfWeek dayOfWeek,
            @ForAll("bucketHourArb") int bucketHour,
            @ForAll("highScoreLists") List<Double> highScores
    ) {
        Long intersectionId = 1L;
        String intersectionLabel = "Test Intersection";
        long idCounter = 1L;

        List<CongestionSnapshot> snapshots = new ArrayList<>();
        for (Double score : highScores) {
            snapshots.add(buildSnapshot(idCounter++, intersectionId, score, dayOfWeek, bucketHour));
        }

        Intersection intersection = buildIntersection(intersectionId, intersectionLabel);

        CongestionSnapshotRepository snapshotRepo = mock(CongestionSnapshotRepository.class);
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class))).thenReturn(snapshots);

        IntersectionRepository intersectionRepo = mock(IntersectionRepository.class);
        when(intersectionRepo.findAllById(any())).thenReturn(List.of(intersection));

        RecurrencePatternRepository patternRepo = mock(RecurrencePatternRepository.class);
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        CampaignRepository campaignRepo = mock(CampaignRepository.class);
        CampaignIntersectionRepository ciRepo = mock(CampaignIntersectionRepository.class);

        // No existing campaigns — overlap check returns empty
        when(ciRepo.findByIntersectionId(intersectionId)).thenReturn(Collections.emptyList());
        when(campaignRepo.findByStatusIn(any())).thenReturn(Collections.emptyList());

        // Capture the saved campaign
        final Campaign[] capturedCampaign = {null};
        when(campaignRepo.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(100L);
            capturedCampaign[0] = c;
            return c;
        });

        when(ciRepo.save(any(CampaignIntersection.class))).thenAnswer(inv -> {
            CampaignIntersection ci = inv.getArgument(0);
            ci.setId(200L);
            return ci;
        });

        PredictiveCampaignServiceImpl service = createService(
                snapshotRepo, intersectionRepo, patternRepo, campaignRepo, ciRepo);

        service.detectAndDraftCampaigns();

        // Verify a campaign was saved
        assertThat(capturedCampaign[0]).isNotNull();
        Campaign campaign = capturedCampaign[0];

        // Verify windowStart is the next occurrence of dayOfWeek on or after today
        LocalDate expectedWindowStart = LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        assertThat(campaign.getWindowStart()).isEqualTo(expectedWindowStart);

        // Verify windowEnd is windowStart + 1 day
        assertThat(campaign.getWindowEnd()).isEqualTo(expectedWindowStart.plusDays(1));

        // Verify title matches expected format
        LocalTime bucketStart = toBucketStart(bucketHour);
        LocalTime bucketEnd = bucketStart.plusHours(BUCKET_SIZE_HOURS);
        String expectedTitle = String.format("Predicted congestion at %s — %s %s–%s",
                intersectionLabel, dayOfWeek, bucketStart, bucketEnd);
        assertThat(campaign.getTitle()).isEqualTo(expectedTitle);

        // Verify status is DRAFT
        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);

        // Verify target amount
        assertThat(campaign.getTargetAmount()).isEqualByComparingTo(DEFAULT_TARGET);
    }

    // -----------------------------------------------------------------------
    // Arbitraries / Providers
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<DayOfWeek> dayOfWeekArb() {
        return Arbitraries.of(DayOfWeek.values());
    }

    /** Even hours that align with 2-hour bucket starts: 0, 2, 4, ..., 22. */
    @Provide
    Arbitrary<Integer> bucketHourArb() {
        return Arbitraries.of(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22);
    }

    /** Lists of high scores guaranteed to have at least MIN_OCCURRENCES entries. */
    @Provide
    Arbitrary<List<Double>> highScoreLists() {
        return Arbitraries.doubles().between(THRESHOLD, 1.0)
                .list().ofMinSize(MIN_OCCURRENCES).ofMaxSize(10);
    }
}
