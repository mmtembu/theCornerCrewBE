package com.cornercrew.app.commuteprofile;

import jakarta.persistence.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "commute_profiles")
public class CommuteProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false, unique = true)
    private Long driverId;

    @Column(name = "origin_latitude", nullable = false)
    private Double originLatitude;

    @Column(name = "origin_longitude", nullable = false)
    private Double originLongitude;

    @Column(name = "destination_latitude", nullable = false)
    private Double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private Double destinationLongitude;

    @Column(name = "departure_start_time", nullable = false)
    private LocalTime departureStartTime;

    @Column(name = "departure_end_time", nullable = false)
    private LocalTime departureEndTime;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public Double getOriginLatitude() { return originLatitude; }
    public void setOriginLatitude(Double originLatitude) { this.originLatitude = originLatitude; }

    public Double getOriginLongitude() { return originLongitude; }
    public void setOriginLongitude(Double originLongitude) { this.originLongitude = originLongitude; }

    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double destinationLatitude) { this.destinationLatitude = destinationLatitude; }

    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double destinationLongitude) { this.destinationLongitude = destinationLongitude; }

    public LocalTime getDepartureStartTime() { return departureStartTime; }
    public void setDepartureStartTime(LocalTime departureStartTime) { this.departureStartTime = departureStartTime; }

    public LocalTime getDepartureEndTime() { return departureEndTime; }
    public void setDepartureEndTime(LocalTime departureEndTime) { this.departureEndTime = departureEndTime; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
