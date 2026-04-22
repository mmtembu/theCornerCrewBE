package com.cornercrew.app.predictive;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "recurrence_patterns")
public class RecurrencePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "time_bucket_start", nullable = false)
    private LocalTime timeBucketStart;

    @Column(name = "time_bucket_end", nullable = false)
    private LocalTime timeBucketEnd;

    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount;

    @Column(name = "average_congestion_score", nullable = false)
    private Double averageCongestionScore;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "pattern_data", columnDefinition = "jsonb")
    private String patternData;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIntersectionId() { return intersectionId; }
    public void setIntersectionId(Long intersectionId) { this.intersectionId = intersectionId; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getTimeBucketStart() { return timeBucketStart; }
    public void setTimeBucketStart(LocalTime timeBucketStart) { this.timeBucketStart = timeBucketStart; }

    public LocalTime getTimeBucketEnd() { return timeBucketEnd; }
    public void setTimeBucketEnd(LocalTime timeBucketEnd) { this.timeBucketEnd = timeBucketEnd; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public Double getAverageCongestionScore() { return averageCongestionScore; }
    public void setAverageCongestionScore(Double averageCongestionScore) { this.averageCongestionScore = averageCongestionScore; }

    public OffsetDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(OffsetDateTime detectedAt) { this.detectedAt = detectedAt; }

    public String getPatternData() { return patternData; }
    public void setPatternData(String patternData) { this.patternData = patternData; }
}
