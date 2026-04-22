package com.cornercrew.app.predictive;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.config.PredictiveProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictiveCampaignServiceTest {

    @Mock
    private CongestionSnapshotRepository snapshotRepo;

    @Mock
    private RecurrencePatternRepository patternRepo;

    @Mock
    private IntersectionRepository intersectionRepo;

    @Mock
    private CampaignRepository campaignRepo;

    @Mock
    private CampaignIntersectionRepository campaignIntersectionRepo;

    private PredictiveProperties predictiveProps;
    private TrafficMonitoringProperties trafficProps;
    private ObjectMapper objectMapper;
    private PredictiveCampaignServiceImpl service;

    @BeforeEach
    void setUp() {
        predictiveProps = new PredictiveProperties();
        trafficProps = new TrafficMonitoringProperties();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        service = new PredictiveCampaignServiceImpl(
                snapshotRepo,
                patternRepo,
                intersectionRepo,
                campaignRepo,
                campaignIntersectionRepo,
                predictiveProps,
                trafficProps,
                objectMapper
        );
    }

    // --- Test 1: Empty snapshot set produces no patterns ---

    @Test
    void detectPatterns_emptySnapshots_returnsEmptyAndNeverSavesPattern() {
        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<RecurrencePatternDto> result = service.detectPatterns();

        assertThat(result).isEmpty();
        verify(patternRepo, never()).save(any(RecurrencePattern.class));
    }

    // --- Test 2: All scores below threshold produces no patterns ---

    @Test
    void detectPatterns_allScoresBelowThreshold_returnsEmpty() {
        // Default congestion threshold is 0.7
        List<CongestionSnapshot> lowSnapshots = List.of(
                buildSnapshot(1L, 1L, 0.3, DayOfWeek.MONDAY, 8),
                buildSnapshot(2L, 1L, 0.5, DayOfWeek.MONDAY, 8),
                buildSnapshot(3L, 1L, 0.69, DayOfWeek.MONDAY, 8),
                buildSnapshot(4L, 1L, 0.1, DayOfWeek.MONDAY, 8)
        );

        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(lowSnapshots);

        List<RecurrencePatternDto> result = service.detectPatterns();

        assertThat(result).isEmpty();
        verify(patternRepo, never()).save(any(RecurrencePattern.class));
    }

    // --- Test 3: Configuration loading (verify defaults) ---

    @Test
    void predictiveProperties_defaults_areCorrect() {
        PredictiveProperties props = new PredictiveProperties();

        assertThat(props.getLookbackWeeks()).isEqualTo(4);
        assertThat(props.getMinOccurrences()).isEqualTo(3);
        assertThat(props.getDefaultTargetAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // --- Test 4: Auto-drafted campaign has DRAFT status ---

    @Test
    void detectAndDraftCampaigns_highCongestionPattern_savesDraftCampaign() {
        // Create enough high-congestion snapshots to trigger a pattern (>= minOccurrences=3)
        Long intersectionId = 1L;
        List<CongestionSnapshot> highSnapshots = List.of(
                buildSnapshot(1L, intersectionId, 0.85, DayOfWeek.TUESDAY, 8),
                buildSnapshot(2L, intersectionId, 0.90, DayOfWeek.TUESDAY, 8),
                buildSnapshot(3L, intersectionId, 0.80, DayOfWeek.TUESDAY, 8)
        );

        when(snapshotRepo.findByRecordedAtAfter(any(OffsetDateTime.class)))
                .thenReturn(highSnapshots);

        // Intersection lookup
        Intersection intersection = buildIntersection(intersectionId, "Oak Ave & Main St");
        when(intersectionRepo.findAllById(any())).thenReturn(List.of(intersection));

        // Pattern save mock
        when(patternRepo.save(any(RecurrencePattern.class))).thenAnswer(inv -> {
            RecurrencePattern p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        // No existing campaigns for overlap check
        when(campaignIntersectionRepo.findByIntersectionId(intersectionId))
                .thenReturn(Collections.emptyList());

        // Campaign save mock
        when(campaignRepo.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        service.detectAndDraftCampaigns();

        // Verify campaignRepo.save() was called once
        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepo, times(1)).save(captor.capture());

        // Verify the saved campaign has status DRAFT
        Campaign savedCampaign = captor.getValue();
        assertThat(savedCampaign.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(savedCampaign.getTitle()).contains("Oak Ave & Main St");
        assertThat(savedCampaign.getTargetAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(savedCampaign.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- Helpers ---

    private CongestionSnapshot buildSnapshot(Long id, Long intersectionId, double score,
                                              DayOfWeek dayOfWeek, int hour) {
        CongestionSnapshot s = new CongestionSnapshot();
        s.setId(id);
        s.setIntersectionId(intersectionId);
        s.setScore(score);
        s.setProvider("test");

        // Build a measuredAt within the lookback window on the given dayOfWeek and hour
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(1);
        while (base.getDayOfWeek() != dayOfWeek) {
            base = base.minusDays(1);
        }
        OffsetDateTime measuredAt = base.withHour(hour).withMinute(15).withSecond(0).withNano(0);
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
}
