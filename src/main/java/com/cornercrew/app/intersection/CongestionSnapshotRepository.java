package com.cornercrew.app.intersection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CongestionSnapshotRepository extends JpaRepository<CongestionSnapshot, Long> {
    
    /**
     * Finds the most recent congestion snapshot for a given intersection.
     * 
     * @param intersectionId the intersection ID
     * @return the most recent snapshot, or empty if none exist
     */
    Optional<CongestionSnapshot> findTopByIntersectionIdOrderByRecordedAtDesc(Long intersectionId);

    /**
     * Finds all congestion snapshots recorded after the given cutoff time.
     *
     * @param cutoff the cutoff timestamp
     * @return snapshots with recordedAt after the cutoff
     */
    List<CongestionSnapshot> findByRecordedAtAfter(OffsetDateTime cutoff);
}
