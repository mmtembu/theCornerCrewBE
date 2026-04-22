package com.cornercrew.app.predictive;

import com.cornercrew.app.campaign.Campaign;
import com.cornercrew.app.campaign.CampaignIntersection;
import com.cornercrew.app.campaign.CampaignIntersectionRepository;
import com.cornercrew.app.campaign.CampaignRepository;
import com.cornercrew.app.campaign.CampaignStatus;
import com.cornercrew.app.config.PredictiveProperties;
import com.cornercrew.app.config.TrafficMonitoringProperties;
import com.cornercrew.app.intersection.CongestionSnapshot;
import com.cornercrew.app.intersection.CongestionSnapshotRepository;
import com.cornercrew.app.intersection.Intersection;
import com.cornercrew.app.intersection.IntersectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictiveCampaignServiceImpl implements PredictiveCampaignService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveCampaignServiceImpl.class);
    private static final int BUCKET_SIZE_HOURS = 2;
    private static final int BUCKETS_PER_DAY = 24 / BUCKET_SIZE_HOURS;

    private final CongestionSnapshotRepository congestionSnapshotRepository;
    private final RecurrencePatternRepository recurrencePatternRepository;
    private final IntersectionRepository intersectionRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignIntersectionRepository campaignIntersectionRepository;
    private final PredictiveProperties predictiveProperties;
    private final TrafficMonitoringProperties trafficMonitoringProperties;
    private final ObjectMapper objectMapper;

    public PredictiveCampaignServiceImpl(
            CongestionSnapshotRepository congestionSnapshotRepository,
            RecurrencePatternRepository recurrencePatternRepository,
            IntersectionRepository intersectionRepository,
            CampaignRepository campaignRepository,
            CampaignIntersectionRepository campaignIntersectionRepository,
            PredictiveProperties predictiveProperties,
            TrafficMonitoringProperties trafficMonitoringProperties,
            ObjectMapper objectMapper) {
        this.congestionSnapshotRepository = congestionSnapshotRepository;
        this.recurrencePatternRepository = recurrencePatternRepository;
        this.intersectionRepository = intersectionRepository;
        this.campaignRepository = campaignRepository;
        this.campaignIntersectionRepository = campaignIntersectionRepository;
        this.predictiveProperties = predictiveProperties;
        this.trafficMonitoringProperties = trafficMonitoringProperties;
        this.objectMapper = objectMapper;
    }

    private static final long SYSTEM_ADMIN_ID = 1L;
    private static final List<CampaignStatus> ACTIVE_STATUSES =
            List.of(CampaignStatus.DRAFT, CampaignStatus.OPEN, CampaignStatus.FUNDED);

    @Override
    @Transactional
    public void detectAndDraftCampaigns() {
        List<RecurrencePatternDto> patterns = detectPatterns();
        log.info("Detected {} recurrence patterns", patterns.size());

        for (RecurrencePatternDto pattern : patterns) {
            LocalDate windowStart = nextDayOfWeek(LocalDate.now(), pattern.dayOfWeek());
            LocalDate windowEnd = windowStart.plusDays(1);

            // Check for existing active campaigns covering the same intersection and overlapping window
            boolean overlapExists = hasOverlappingCampaign(pattern.intersectionId(), windowStart, windowEnd);

            if (!overlapExists) {
                // Create a DRAFT campaign
                String title = String.format("Predicted congestion at %s — %s %s–%s",
                        pattern.intersectionLabel(),
                        pattern.dayOfWeek(),
                        pattern.timeBucketStart(),
                        pattern.timeBucketEnd());

                Campaign campaign = new Campaign();
                campaign.setTitle(title);
                campaign.setStatus(CampaignStatus.DRAFT);
                campaign.setTargetAmount(predictiveProperties.getDefaultTargetAmount());
                campaign.setCurrentAmount(BigDecimal.ZERO);
                campaign.setWindowStart(windowStart);
                campaign.setWindowEnd(windowEnd);
                campaign.setCreatedByAdminId(SYSTEM_ADMIN_ID);

                Campaign saved = campaignRepository.save(campaign);

                // Link the campaign to the intersection
                CampaignIntersection ci = new CampaignIntersection();
                ci.setCampaignId(saved.getId());
                ci.setIntersectionId(pattern.intersectionId());
                campaignIntersectionRepository.save(ci);

                log.info("Auto-drafted campaign {} for {}, pattern: {} {}–{}, avg score: {}",
                        saved.getId(),
                        pattern.intersectionLabel(),
                        pattern.dayOfWeek(),
                        pattern.timeBucketStart(),
                        pattern.timeBucketEnd(),
                        pattern.averageCongestionScore());
            }
        }
    }

    /**
     * Checks whether an active campaign (DRAFT, OPEN, or FUNDED) already exists
     * for the given intersection with a window that overlaps [windowStart, windowEnd).
     */
    private boolean hasOverlappingCampaign(Long intersectionId, LocalDate windowStart, LocalDate windowEnd) {
        List<CampaignIntersection> links = campaignIntersectionRepository.findByIntersectionId(intersectionId);
        if (links.isEmpty()) {
            return false;
        }

        Set<Long> campaignIds = links.stream()
                .map(CampaignIntersection::getCampaignId)
                .collect(Collectors.toSet());

        List<Campaign> activeCampaigns = campaignRepository.findByStatusIn(ACTIVE_STATUSES);

        return activeCampaigns.stream()
                .filter(c -> campaignIds.contains(c.getId()))
                .anyMatch(c -> !c.getWindowStart().isAfter(windowEnd)
                        && !c.getWindowEnd().isBefore(windowStart));
    }

    /**
     * Returns the next occurrence of the given day-of-week on or after the given date.
     */
    private LocalDate nextDayOfWeek(LocalDate from, DayOfWeek targetDay) {
        return from.with(TemporalAdjusters.nextOrSame(targetDay));
    }

    @Override
    @Transactional
    public List<RecurrencePatternDto> detectPatterns() {
        OffsetDateTime cutoff = OffsetDateTime.now()
                .minusWeeks(predictiveProperties.getLookbackWeeks());

        List<CongestionSnapshot> snapshots =
                congestionSnapshotRepository.findByRecordedAtAfter(cutoff);

        double congestionThreshold = trafficMonitoringProperties.getCongestionThreshold();
        int minOccurrences = predictiveProperties.getMinOccurrences();

        // Group snapshots by (intersectionId, dayOfWeek, timeBucketStart)
        Map<GroupKey, List<CongestionSnapshot>> groups = snapshots.stream()
                .collect(Collectors.groupingBy(s -> {
                    DayOfWeek dow = s.getMeasuredAt().getDayOfWeek();
                    LocalTime bucketStart = toBucketStart(s.getMeasuredAt().toLocalTime());
                    return new GroupKey(s.getIntersectionId(), dow, bucketStart);
                }));

        // Pre-load intersection labels for all intersection IDs in the groups
        Set<Long> intersectionIds = groups.keySet().stream()
                .map(GroupKey::intersectionId)
                .collect(Collectors.toSet());
        Map<Long, String> labelMap = intersectionRepository.findAllById(intersectionIds)
                .stream()
                .collect(Collectors.toMap(Intersection::getId, Intersection::getLabel));

        List<RecurrencePatternDto> result = new ArrayList<>();

        for (Map.Entry<GroupKey, List<CongestionSnapshot>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<CongestionSnapshot> groupSnapshots = entry.getValue();

            // Filter to high-congestion snapshots
            List<CongestionSnapshot> highCongestion = groupSnapshots.stream()
                    .filter(s -> s.getScore() >= congestionThreshold)
                    .toList();

            if (highCongestion.size() >= minOccurrences) {
                double avgScore = highCongestion.stream()
                        .mapToDouble(CongestionSnapshot::getScore)
                        .average()
                        .orElse(0.0);

                LocalTime bucketEnd = key.bucketStart().plusHours(BUCKET_SIZE_HOURS);

                // Build audit JSON with snapshot IDs and scores
                String patternData = buildPatternData(highCongestion);

                RecurrencePattern pattern = new RecurrencePattern();
                pattern.setIntersectionId(key.intersectionId());
                pattern.setDayOfWeek(key.dayOfWeek());
                pattern.setTimeBucketStart(key.bucketStart());
                pattern.setTimeBucketEnd(bucketEnd);
                pattern.setOccurrenceCount(highCongestion.size());
                pattern.setAverageCongestionScore(avgScore);
                pattern.setDetectedAt(OffsetDateTime.now());
                pattern.setPatternData(patternData);

                RecurrencePattern saved = recurrencePatternRepository.save(pattern);

                String label = labelMap.getOrDefault(key.intersectionId(),
                        "Intersection #" + key.intersectionId());

                result.add(new RecurrencePatternDto(
                        saved.getId(),
                        saved.getIntersectionId(),
                        label,
                        saved.getDayOfWeek(),
                        saved.getTimeBucketStart(),
                        saved.getTimeBucketEnd(),
                        saved.getOccurrenceCount(),
                        saved.getAverageCongestionScore(),
                        saved.getDetectedAt()
                ));
            }
        }

        return result;
    }

    /**
     * Assigns a LocalTime to its 2-hour bucket start.
     * E.g., 07:30 → 06:00, 00:15 → 00:00, 23:59 → 22:00.
     */
    private LocalTime toBucketStart(LocalTime time) {
        int hour = time.getHour();
        int bucketStartHour = (hour / BUCKET_SIZE_HOURS) * BUCKET_SIZE_HOURS;
        return LocalTime.of(bucketStartHour, 0);
    }

    /**
     * Builds a JSON string with audit info for the pattern (snapshot IDs and scores).
     */
    private String buildPatternData(List<CongestionSnapshot> highCongestion) {
        List<Map<String, Object>> auditEntries = highCongestion.stream()
                .map(s -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("snapshotId", s.getId());
                    entry.put("score", s.getScore());
                    entry.put("measuredAt", s.getMeasuredAt().toString());
                    return entry;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(
                    Map.of("snapshots", auditEntries));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize pattern data", e);
            return "{}";
        }
    }

    /**
     * Composite key for grouping snapshots by intersection, day-of-week, and time bucket.
     */
    private record GroupKey(Long intersectionId, DayOfWeek dayOfWeek, LocalTime bucketStart) {}
}
