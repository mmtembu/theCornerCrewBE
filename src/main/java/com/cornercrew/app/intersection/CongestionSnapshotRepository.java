package com.cornercrew.app.intersection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CongestionSnapshotRepository extends JpaRepository<CongestionSnapshot, Long> {
    
    /**
     * Finds the most recent congestion snapshot for a given intersection.
     * 
     * @param intersectionId the intersection ID
     * @return the most recent snapshot, or empty if none exist
     */
    Optional<CongestionSnapshot> findTopByIntersectionIdOrderByRecordedAtDesc(Long intersectionId);
}
