package com.cornercrew.app.intersection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntersectionRepository extends JpaRepository<Intersection, Long> {

    Page<Intersection> findByStatus(IntersectionStatus status, Pageable pageable);

    Optional<Intersection> findByLatitudeAndLongitude(Double latitude, Double longitude);
}
