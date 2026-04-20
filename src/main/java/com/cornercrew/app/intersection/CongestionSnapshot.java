package com.cornercrew.app.intersection;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "congestion_snapshots")
public class CongestionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Column(nullable = false)
    private Double score;

    @Column(name = "raw_level")
    private String rawLevel;

    @Column(nullable = false)
    private String provider;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIntersectionId() { return intersectionId; }
    public void setIntersectionId(Long intersectionId) { this.intersectionId = intersectionId; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getRawLevel() { return rawLevel; }
    public void setRawLevel(String rawLevel) { this.rawLevel = rawLevel; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime measuredAt) { this.measuredAt = measuredAt; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
