package com.cornercrew.app.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByAssignmentId(Long assignmentId);

    Optional<Review> findByAssignmentIdAndDriverId(Long assignmentId, Long driverId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.assignmentId = :assignmentId")
    Double averageRatingByAssignmentId(@Param("assignmentId") Long assignmentId);

    long countByAssignmentId(Long assignmentId);
}
