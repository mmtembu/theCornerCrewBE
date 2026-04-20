package com.cornercrew.app.intersection;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "intersections")
public class Intersection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column
    private String description;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IntersectionType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IntersectionStatus status;

    @Column(name = "congestion_score")
    private Double congestionScore;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public IntersectionType getType() { return type; }
    public void setType(IntersectionType type) { this.type = type; }

    public IntersectionStatus getStatus() { return status; }
    public void setStatus(IntersectionStatus status) { this.status = status; }

    public Double getCongestionScore() { return congestionScore; }
    public void setCongestionScore(Double congestionScore) { this.congestionScore = congestionScore; }

    public OffsetDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
